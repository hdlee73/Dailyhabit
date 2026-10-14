package com.hdlee73.dailyhabit.sync

import java.io.File
import java.sql.DriverManager

/**
 * 테스트용 기기 하나 (SQLite 파일 + 사진 폴더).
 * phone = 안드로이드 Room이 v1.7.0까지 만든 표 모양, pc = 윈도우 앱의 표 모양.
 */
class TestDevice(dir: File, name: String, roomSchema: Boolean) {
    val file = File(dir, "$name.db")
    val conn = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
    val db = JdbcSyncDb(conn)
    val photoDir = File(dir, "$name-photos").apply { mkdirs() }
    val photos = object : PhotoStore {
        override fun has(name: String) = File(photoDir, name).exists()
        override fun read(name: String) = File(photoDir, name).takeIf { it.exists() }?.readBytes()
        override fun write(name: String, bytes: ByteArray) = File(photoDir, name).writeBytes(bytes)
    }

    init {
        (if (roomSchema) ROOM_V7 else WINDOWS).forEach { db.exec(it) }
    }

    fun ensure() = SyncSchema.ensure(db)

    // ── 앱이 하는 일 흉내 (DAO와 같은 SQL)
    fun addTodo(title: String, createdAt: Long = System.nanoTime(), done: Boolean = false): Long {
        db.exec("INSERT INTO todos (title, done, createdAt, priority, category, memo, remind) VALUES (?, ?, ?, 1, '', '', 0)", title, done, createdAt)
        return db.lastInsertId()
    }

    fun setTodoTitle(id: Long, title: String) = db.exec("UPDATE todos SET title = ? WHERE id = ?", title, id)
    fun deleteTodo(id: Long) = db.exec("DELETE FROM todos WHERE id = ?", id)
    fun todoId(title: String): Long? = db.query("SELECT id FROM todos WHERE title = ?", title).firstOrNull()?.long("id")

    fun addRoutine(title: String, kind: Int = 0, day: Long = 20000): Long {
        db.exec(
            "INSERT INTO routines (title, emoji, color, days, reminderEnabled, reminderMinute, createdEpochDay, archived, sortOrder, kind) VALUES (?, '✨', 0, 127, 0, 480, ?, 0, 0, ?)",
            title, day, kind,
        )
        return db.lastInsertId()
    }

    fun routineId(title: String): Long? = db.query("SELECT id FROM routines WHERE title = ?", title).firstOrNull()?.long("id")
    fun check(routineId: Long, day: Long) = db.exec("INSERT OR REPLACE INTO routine_checks (routineId, epochDay, checkedAt) VALUES (?, ?, ?)", routineId, day, System.currentTimeMillis())
    fun uncheck(routineId: Long, day: Long) = db.exec("DELETE FROM routine_checks WHERE routineId = ? AND epochDay = ?", routineId, day)

    fun addRestaurant(name: String, createdAt: Long = System.nanoTime()): Long {
        db.exec(
            "INSERT INTO restaurants (name, category, menu, address, link, rating, comment, memo, tags, price, wish, revisit, visitCount, lastVisitEpochDay, createdAt) " +
                "VALUES (?, '한식', '', '', '', 4, '', '', '', 0, 0, 0, 0, NULL, ?)",
            name, createdAt,
        )
        return db.lastInsertId()
    }

    fun addVisit(restaurantId: Long, day: Long, note: String = "", photo: String = ""): Long {
        db.exec("INSERT INTO restaurant_visits (restaurantId, epochDay, note, photo) VALUES (?, ?, ?, ?)", restaurantId, day, note, photo)
        val id = db.lastInsertId()
        // 화면의 syncVisits와 같이 맛집의 방문 횟수를 맞춘다
        db.exec(
            "UPDATE restaurants SET visitCount = (SELECT COUNT(*) FROM restaurant_visits WHERE restaurantId = ?), lastVisitEpochDay = (SELECT MAX(epochDay) FROM restaurant_visits WHERE restaurantId = ?) WHERE id = ?",
            restaurantId, restaurantId, restaurantId,
        )
        return id
    }

    fun diary(day: Long, text: String) = db.exec("INSERT OR REPLACE INTO diary_entries (epochDay, text, mood, updatedAt) VALUES (?, ?, 1, ?)", day, text, System.currentTimeMillis())

    fun addBook(title: String, createdAt: Long = System.nanoTime()): Long {
        db.exec(
            "INSERT INTO books (title, author, totalPages, currentPage, status, rating, startEpochDay, endEpochDay, review, createdAt) VALUES (?, '저자', 300, 10, 0, 0, NULL, NULL, '', ?)",
            title, createdAt,
        )
        return db.lastInsertId()
    }

    fun addNote(bookId: Long, text: String, createdAt: Long = System.nanoTime()): Long {
        db.exec("INSERT INTO book_notes (bookId, text, page, createdAt) VALUES (?, ?, 1, ?)", bookId, text, createdAt)
        return db.lastInsertId()
    }

    fun addShopping(name: String, createdAt: Long = System.nanoTime()): Long {
        db.exec("INSERT INTO shopping_items (name, qty, category, checked, createdAt, checkedAt) VALUES (?, '', '기타', 0, ?, NULL)", name, createdAt)
        return db.lastInsertId()
    }

    fun count(table: String): Long = db.query("SELECT COUNT(*) AS n FROM $table").first().long("n")

    /**
     * 기기마다 다른 id를 빼고, 부모는 이름으로 바꾼 내용 전체. 두 기기의 이 값이 같으면 같은 기록을 가진 것.
     */
    fun dump(): Map<String, List<String>> {
        val routineName = db.query("SELECT id, title FROM routines").associate { it.long("id") to it.str("title") }
        val restaurantName = db.query("SELECT id, name FROM restaurants").associate { it.long("id") to it.str("name") }
        val bookName = db.query("SELECT id, title FROM books").associate { it.long("id") to it.str("title") }
        return SyncSchema.tables.associate { t ->
            t.name to SyncSchema.selectAll(db, t).map { row ->
                t.cols.joinToString("|") { c ->
                    val v = row[c.name]
                    when (c.name) {
                        "routineId" -> "R:" + routineName[v as Long]
                        "restaurantId" -> "S:" + restaurantName[v as Long]
                        "bookId" -> "B:" + bookName[v as Long]
                        else -> v.toString()
                    }
                }
            }.sorted()
        }
    }

    fun close() = conn.close()

    companion object {
        /** v1.7.0까지의 Room 마이그레이션이 만든 표 */
        val ROOM_V7 = listOf(
            "CREATE TABLE IF NOT EXISTS `todos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `done` INTEGER NOT NULL, `dueEpochDay` INTEGER, `createdAt` INTEGER NOT NULL, `priority` INTEGER NOT NULL DEFAULT 1, `category` TEXT NOT NULL DEFAULT '', `memo` TEXT NOT NULL DEFAULT '', `dueMinute` INTEGER, `remind` INTEGER NOT NULL DEFAULT 0, `completedAt` INTEGER)",
            "CREATE TABLE IF NOT EXISTS `routines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `emoji` TEXT NOT NULL, `color` INTEGER NOT NULL, `days` INTEGER NOT NULL, `reminderEnabled` INTEGER NOT NULL, `reminderMinute` INTEGER NOT NULL, `createdEpochDay` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `kind` INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS `routine_checks` (`routineId` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, `checkedAt` INTEGER NOT NULL, PRIMARY KEY(`routineId`, `epochDay`))",
            "CREATE TABLE IF NOT EXISTS `restaurants` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `menu` TEXT NOT NULL, `address` TEXT NOT NULL, `link` TEXT NOT NULL, `rating` INTEGER NOT NULL, `comment` TEXT NOT NULL, `memo` TEXT NOT NULL, `tags` TEXT NOT NULL, `price` INTEGER NOT NULL, `wish` INTEGER NOT NULL, `revisit` INTEGER NOT NULL, `visitCount` INTEGER NOT NULL, `lastVisitEpochDay` INTEGER, `createdAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `diary_entries` (`epochDay` INTEGER NOT NULL, `text` TEXT NOT NULL, `mood` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`epochDay`))",
            "CREATE TABLE IF NOT EXISTS `shopping_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `qty` TEXT NOT NULL, `category` TEXT NOT NULL, `checked` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `checkedAt` INTEGER)",
            "CREATE TABLE IF NOT EXISTS `books` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `author` TEXT NOT NULL, `totalPages` INTEGER NOT NULL, `currentPage` INTEGER NOT NULL, `status` INTEGER NOT NULL, `rating` INTEGER NOT NULL, `startEpochDay` INTEGER, `endEpochDay` INTEGER, `review` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `book_notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookId` INTEGER NOT NULL, `text` TEXT NOT NULL, `page` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `restaurant_visits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `restaurantId` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, `note` TEXT NOT NULL, `photo` TEXT NOT NULL DEFAULT '')",
        )

        /** 윈도우 앱(AppDatabase.initSchema)의 표 */
        val WINDOWS = listOf(
            "CREATE TABLE IF NOT EXISTS todos (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, dueEpochDay INTEGER, createdAt INTEGER NOT NULL, priority INTEGER NOT NULL DEFAULT 1, category TEXT NOT NULL DEFAULT '', memo TEXT NOT NULL DEFAULT '', dueMinute INTEGER, remind INTEGER NOT NULL DEFAULT 0, completedAt INTEGER)",
            "CREATE TABLE IF NOT EXISTS routines (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, emoji TEXT NOT NULL, color INTEGER NOT NULL, days INTEGER NOT NULL, reminderEnabled INTEGER NOT NULL, reminderMinute INTEGER NOT NULL, createdEpochDay INTEGER NOT NULL, archived INTEGER NOT NULL, sortOrder INTEGER NOT NULL, kind INTEGER NOT NULL DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS routine_checks (routineId INTEGER NOT NULL, epochDay INTEGER NOT NULL, checkedAt INTEGER NOT NULL, PRIMARY KEY(routineId, epochDay))",
            "CREATE TABLE IF NOT EXISTS restaurants (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, category TEXT NOT NULL, menu TEXT NOT NULL, address TEXT NOT NULL, link TEXT NOT NULL, rating INTEGER NOT NULL, comment TEXT NOT NULL, memo TEXT NOT NULL, tags TEXT NOT NULL, price INTEGER NOT NULL, wish INTEGER NOT NULL, revisit INTEGER NOT NULL, visitCount INTEGER NOT NULL, lastVisitEpochDay INTEGER, createdAt INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS restaurant_visits (id INTEGER PRIMARY KEY AUTOINCREMENT, restaurantId INTEGER NOT NULL, epochDay INTEGER NOT NULL, note TEXT NOT NULL, photo TEXT NOT NULL DEFAULT '')",
            "CREATE TABLE IF NOT EXISTS diary_entries (epochDay INTEGER PRIMARY KEY, text TEXT NOT NULL, mood INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS shopping_items (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, qty TEXT NOT NULL, category TEXT NOT NULL, checked INTEGER NOT NULL, createdAt INTEGER NOT NULL, checkedAt INTEGER)",
            "CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, author TEXT NOT NULL, totalPages INTEGER NOT NULL, currentPage INTEGER NOT NULL, status INTEGER NOT NULL, rating INTEGER NOT NULL, startEpochDay INTEGER, endEpochDay INTEGER, review TEXT NOT NULL, createdAt INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS book_notes (id INTEGER PRIMARY KEY AUTOINCREMENT, bookId INTEGER NOT NULL, text TEXT NOT NULL, page INTEGER NOT NULL, createdAt INTEGER NOT NULL)",
        )
    }
}

/** HTTP 없이 휴대폰 ↔ PC 한 번 주고받기 */
class DirectLink(private val phone: TestDevice, private val pc: TestDevice) {
    var state = SyncEngine.ClientState.FRESH
    var last: SyncEngine.ClientResult? = null

    fun sync(): SyncEngine.ClientResult {
        val r = SyncEngine.clientSync(phone.db, state) { req ->
            // 실제처럼 문자열로 바꿨다가 다시 읽는다
            val resp = SyncEngine.serve(pc.db, org.json.JSONObject(req.toString()))
            org.json.JSONObject(resp.toString())
        }
        state = r.state
        last = r
        return r
    }
}
