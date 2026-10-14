package com.hdlee73.dailyhabit.data

import android.content.Context
import androidx.room.withTransaction
import com.hdlee73.dailyhabit.BuildConfig
import com.hdlee73.dailyhabit.notify.DailyScheduler
import com.hdlee73.dailyhabit.notify.Reminders
import com.hdlee73.dailyhabit.sync.SyncSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 모든 기록을 JSON 파일 하나로 내보내고 되돌린다 (천주교인용 ↔ 일반용 사이에서도 호환) */
object Backup {
    private const val FORMAT = 1

    data class Summary(val counts: List<Pair<String, Int>>) {
        val text: String get() = counts.filter { it.second > 0 }.joinToString(" · ") { "${it.first} ${it.second}" }.ifEmpty { "기록 없음" }
    }

    private fun JSONObject.longOrNull(name: String): Long? = if (isNull(name)) null else getLong(name)
    private fun JSONObject.intOrNull(name: String): Int? = if (isNull(name)) null else getInt(name)
    private fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> = List(length()) { f(getJSONObject(it)) }
    private fun <T> JSONObject.list(name: String, f: (JSONObject) -> T): List<T> =
        optJSONArray(name)?.mapObjects(f) ?: emptyList()
    private fun <T> Iterable<T>.toJson(f: (T) -> JSONObject) = JSONArray().also { a -> forEach { a.put(f(it)) } }

    suspend fun export(context: Context): String {
        val db = AppDatabase.get(context)
        val settings = AppSettings(context)
        val root = JSONObject()
        root.put("app", "DailyHabit")
        root.put("format", FORMAT)
        root.put("appVersion", BuildConfig.VERSION_NAME)
        root.put("exportedAt", System.currentTimeMillis())
        val year = java.time.LocalDate.now().year
        root.put(
            "settings",
            JSONObject().put("dailyEnabled", settings.dailyEnabled).put("dailyMinute", settings.dailyMinute)
                .put("readingGoalYear", year).put("readingGoal", settings.readingGoal(year)),
        )

        root.put("todos", db.todoDao().all().toJson {
            JSONObject().put("id", it.id).put("title", it.title).put("done", it.done).put("dueEpochDay", it.dueEpochDay)
                .put("createdAt", it.createdAt).put("priority", it.priority).put("category", it.category).put("memo", it.memo)
                .put("dueMinute", it.dueMinute).put("remind", it.remind).put("completedAt", it.completedAt)
        })
        root.put("routines", db.routineDao().allRoutines().toJson {
            JSONObject().put("id", it.id).put("title", it.title).put("emoji", it.emoji).put("color", it.color).put("days", it.days)
                .put("reminderEnabled", it.reminderEnabled).put("reminderMinute", it.reminderMinute)
                .put("createdEpochDay", it.createdEpochDay).put("archived", it.archived).put("sortOrder", it.sortOrder).put("kind", it.kind)
        })
        root.put("routineChecks", db.routineDao().allChecks().toJson {
            JSONObject().put("routineId", it.routineId).put("epochDay", it.epochDay).put("checkedAt", it.checkedAt)
        })
        root.put("restaurants", db.restaurantDao().all().toJson {
            JSONObject().put("id", it.id).put("name", it.name).put("category", it.category).put("menu", it.menu)
                .put("address", it.address).put("link", it.link).put("rating", it.rating).put("comment", it.comment)
                .put("memo", it.memo).put("tags", it.tags).put("price", it.price).put("wish", it.wish)
                .put("revisit", it.revisit).put("visitCount", it.visitCount).put("lastVisitEpochDay", it.lastVisitEpochDay)
                .put("createdAt", it.createdAt)
        })
        root.put("restaurantVisits", db.visitDao().all().toJson {
            JSONObject().put("id", it.id).put("restaurantId", it.restaurantId).put("epochDay", it.epochDay).put("note", it.note).put("photo", it.photo)
        })
        root.put("diary", db.diaryDao().all().toJson {
            JSONObject().put("epochDay", it.epochDay).put("text", it.text).put("mood", it.mood).put("updatedAt", it.updatedAt)
        })
        root.put("shopping", db.shoppingDao().all().toJson {
            JSONObject().put("id", it.id).put("name", it.name).put("qty", it.qty).put("category", it.category)
                .put("checked", it.checked).put("createdAt", it.createdAt).put("checkedAt", it.checkedAt)
        })
        root.put("books", db.bookDao().allBooks().toJson {
            JSONObject().put("id", it.id).put("title", it.title).put("author", it.author).put("totalPages", it.totalPages)
                .put("currentPage", it.currentPage).put("status", it.status).put("rating", it.rating)
                .put("startEpochDay", it.startEpochDay).put("endEpochDay", it.endEpochDay).put("review", it.review)
                .put("createdAt", it.createdAt)
        })
        root.put("bookNotes", db.bookDao().allNotes().toJson {
            JSONObject().put("id", it.id).put("bookId", it.bookId).put("text", it.text).put("page", it.page)
                .put("createdAt", it.createdAt)
        })
        // 동기화용 고유 번호: 다른 기기에서 되돌린 뒤 동기화해도 같은 기록이 둘로 늘지 않게 한다
        root.put("syncUids", JSONObject(withContext(Dispatchers.IO) { SyncSchema.exportUids(AndroidSyncDb.of(db)) }))
        settings.lastBackup = System.currentTimeMillis()
        return root.toString(2)
    }

    /** 파일을 먼저 끝까지 읽어 보고, 문제가 없을 때만 기존 기록을 바꾼다. 형식이 틀리면 예외 */
    suspend fun restore(context: Context, json: String): Summary {
        val root = JSONObject(json)
        require(root.optString("app") == "DailyHabit") { "DailyHabit 백업 파일이 아니에요" }
        require(root.optInt("format", 0) in 1..FORMAT) { "이 앱보다 새로운 형식의 백업 파일이에요. 앱을 먼저 업데이트해 주세요" }

        val todos = root.list("todos") {
            Todo(
                id = it.getLong("id"), title = it.getString("title"), done = it.getBoolean("done"),
                dueEpochDay = it.longOrNull("dueEpochDay"), createdAt = it.getLong("createdAt"),
                priority = it.getInt("priority"), category = it.getString("category"), memo = it.getString("memo"),
                dueMinute = it.intOrNull("dueMinute"), remind = it.getBoolean("remind"), completedAt = it.longOrNull("completedAt"),
            )
        }
        val routines = root.list("routines") {
            Routine(
                id = it.getLong("id"), title = it.getString("title"), emoji = it.getString("emoji"), color = it.getInt("color"),
                days = it.getInt("days"), reminderEnabled = it.getBoolean("reminderEnabled"),
                reminderMinute = it.getInt("reminderMinute"), createdEpochDay = it.getLong("createdEpochDay"),
                archived = it.getBoolean("archived"), sortOrder = it.getInt("sortOrder"), kind = it.optInt("kind", 0),
            )
        }
        val checks = root.list("routineChecks") {
            RoutineCheck(it.getLong("routineId"), it.getLong("epochDay"), it.getLong("checkedAt"))
        }
        val restaurants = root.list("restaurants") {
            Restaurant(
                id = it.getLong("id"), name = it.getString("name"), category = it.getString("category"), menu = it.getString("menu"),
                address = it.getString("address"), link = it.getString("link"), rating = it.getInt("rating"),
                comment = it.getString("comment"), memo = it.getString("memo"), tags = it.getString("tags"),
                price = it.getInt("price"), wish = it.getBoolean("wish"), revisit = it.getBoolean("revisit"),
                visitCount = it.getInt("visitCount"), lastVisitEpochDay = it.longOrNull("lastVisitEpochDay"),
                createdAt = it.getLong("createdAt"),
            )
        }
        val visits: List<RestaurantVisit> = if (root.has("restaurantVisits")) {
            root.list("restaurantVisits") {
                RestaurantVisit(it.getLong("id"), it.getLong("restaurantId"), it.getLong("epochDay"), it.optString("note"), it.optString("photo"))
            }
        } else {
            // 방문 기록이 없던 예전 백업: 마지막 방문일을 방문 기록 하나로 만든다
            restaurants.filter { it.lastVisitEpochDay != null }.mapIndexed { i, r ->
                RestaurantVisit(id = i + 1L, restaurantId = r.id, epochDay = r.lastVisitEpochDay!!)
            }
        }
        val diary = root.list("diary") {
            DiaryEntry(it.getLong("epochDay"), it.getString("text"), it.getInt("mood"), it.getLong("updatedAt"))
        }
        val shopping = root.list("shopping") {
            ShoppingItem(
                id = it.getLong("id"), name = it.getString("name"), qty = it.getString("qty"), category = it.getString("category"),
                checked = it.getBoolean("checked"), createdAt = it.getLong("createdAt"), checkedAt = it.longOrNull("checkedAt"),
            )
        }
        val books = root.list("books") {
            Book(
                id = it.getLong("id"), title = it.getString("title"), author = it.getString("author"),
                totalPages = it.getInt("totalPages"), currentPage = it.getInt("currentPage"), status = it.getInt("status"),
                rating = it.getInt("rating"), startEpochDay = it.longOrNull("startEpochDay"),
                endEpochDay = it.longOrNull("endEpochDay"), review = it.getString("review"), createdAt = it.getLong("createdAt"),
            )
        }
        val notes = root.list("bookNotes") {
            BookNote(it.getLong("id"), it.getLong("bookId"), it.getString("text"), it.getInt("page"), it.getLong("createdAt"))
        }

        val uids = root.optJSONObject("syncUids")?.let { o ->
            o.keys().asSequence().associateWith { k -> o.optJSONObject(k)?.let { m -> m.keys().asSequence().associateWith { m.getString(it) } }.orEmpty() }
        }.orEmpty()

        val db = AppDatabase.get(context)
        db.withTransaction {
            db.todoDao().clear(); db.todoDao().insertAll(todos)
            db.routineDao().clearRoutines(); db.routineDao().clearAllChecks()
            db.routineDao().insertRoutines(routines); db.routineDao().insertChecks(checks)
            db.restaurantDao().clear(); db.restaurantDao().insertAll(restaurants)
            db.visitDao().clear(); db.visitDao().insertAll(visits)
            db.diaryDao().clear(); db.diaryDao().insertAll(diary)
            db.shoppingDao().clear(); db.shoppingDao().insertAll(shopping)
            db.bookDao().clearBooks(); db.bookDao().clearNotes()
            db.bookDao().insertBooks(books); db.bookDao().insertNotes(notes)
            // 되돌린 기록에 백업 파일의 고유 번호를 붙이고, 다음 동기화 때 PC에도 반영되게 표시한다
            SyncSchema.afterRestore(AndroidSyncDb(db.openHelper.writableDatabase), uids)
        }

        root.optJSONObject("settings")?.let {
            val settings = AppSettings(context)
            if (it.has("dailyEnabled")) settings.dailyEnabled = it.getBoolean("dailyEnabled")
            if (it.has("dailyMinute")) settings.dailyMinute = it.getInt("dailyMinute")
            if (it.has("readingGoalYear") && it.has("readingGoal")) settings.setReadingGoal(it.getInt("readingGoalYear"), it.getInt("readingGoal"))
        }
        DailyScheduler.schedule(context)
        Reminders.rescheduleAll(context)

        return Summary(
            listOf(
                "할 일" to todos.size, "루틴" to routines.size, "맛집" to restaurants.size, "일기" to diary.size,
                "장보기" to shopping.size, "책" to books.size, "밑줄" to notes.size,
            )
        )
    }
}
