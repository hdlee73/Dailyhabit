package com.hdlee73.dailyhabit.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// ───────────── 할 일 ─────────────

enum class Priority(val label: String) { LOW("낮음"), NORMAL("보통"), HIGH("높음") }

val TODO_CATEGORIES = listOf("개인", "가정", "일", "신앙", "건강", "쇼핑")

@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val done: Boolean = false,
    /** 마감일 (epochDay) */
    val dueEpochDay: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** 0 낮음, 1 보통, 2 높음 */
    @ColumnInfo(defaultValue = "1") val priority: Int = 1,
    @ColumnInfo(defaultValue = "''") val category: String = "",
    @ColumnInfo(defaultValue = "''") val memo: String = "",
    /** 마감 시각 (자정부터 분), 없으면 종일 */
    val dueMinute: Int? = null,
    /** 마감 시각에 알림 */
    @ColumnInfo(defaultValue = "0") val remind: Boolean = false,
    val completedAt: Long? = null,
) {
    val priorityEnum: Priority get() = Priority.entries.getOrElse(priority) { Priority.NORMAL }
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY done ASC, CASE WHEN dueEpochDay IS NULL THEN 1 ELSE 0 END, dueEpochDay ASC, priority DESC, createdAt DESC")
    fun observeAll(): Flow<List<Todo>>

    @Query("SELECT * FROM todos WHERE done = 0 ORDER BY CASE WHEN dueEpochDay IS NULL THEN 1 ELSE 0 END, dueEpochDay ASC, priority DESC, createdAt DESC")
    suspend fun pending(): List<Todo>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun byId(id: Long): Todo?

    @Query("SELECT * FROM todos WHERE done = 0 AND remind = 1 AND dueEpochDay IS NOT NULL AND dueMinute IS NOT NULL")
    suspend fun withReminders(): List<Todo>

    @Insert
    suspend fun insert(todo: Todo): Long

    @Update
    suspend fun update(todo: Todo)

    @Delete
    suspend fun delete(todo: Todo)

    @Query("DELETE FROM todos WHERE done = 1")
    suspend fun clearDone()

    @Query("SELECT * FROM todos")
    suspend fun all(): List<Todo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<Todo>)

    @Query("DELETE FROM todos")
    suspend fun clear()
}

// ───────────── 매일 루틴 ─────────────

@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val emoji: String = "✨",
    /** RoutinePalette 인덱스 */
    val color: Int = 0,
    /** 반복 요일 비트마스크 (월=1, 화=2, … 일=64) */
    val days: Int = ALL_DAYS,
    val reminderEnabled: Boolean = false,
    /** 알림 시각 (자정부터 분) */
    val reminderMinute: Int = 8 * 60,
    val createdEpochDay: Long,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    /** 0 일반, 1 오늘의 말씀 읽기, 2 기도하기 (천주교인용 자동 체크 루틴) */
    @ColumnInfo(defaultValue = "0") val kind: Int = 0,
) {
    fun activeOn(dayOfWeek: java.time.DayOfWeek): Boolean = days and (1 shl (dayOfWeek.value - 1)) != 0

    companion object {
        const val ALL_DAYS = 0b1111111
    }
}

@Entity(tableName = "routine_checks", primaryKeys = ["routineId", "epochDay"])
data class RoutineCheck(
    val routineId: Long,
    val epochDay: Long,
    val checkedAt: Long = System.currentTimeMillis(),
)

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines WHERE archived = 0 ORDER BY sortOrder ASC, id ASC")
    fun observeActive(): Flow<List<Routine>>

    @Query("SELECT * FROM routines WHERE archived = 0 ORDER BY sortOrder ASC, id ASC")
    suspend fun active(): List<Routine>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun byId(id: Long): Routine?

    @Query("SELECT * FROM routine_checks")
    fun observeChecks(): Flow<List<RoutineCheck>>

    @Query("SELECT COUNT(*) FROM routine_checks WHERE routineId = :routineId AND epochDay = :epochDay")
    suspend fun isChecked(routineId: Long, epochDay: Long): Int

    @Query("SELECT * FROM routine_checks WHERE epochDay = :epochDay")
    suspend fun checksOn(epochDay: Long): List<RoutineCheck>

    @Insert
    suspend fun insert(routine: Routine): Long

    @Update
    suspend fun update(routine: Routine)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun check(check: RoutineCheck)

    @Query("DELETE FROM routine_checks WHERE routineId = :routineId AND epochDay = :epochDay")
    suspend fun uncheck(routineId: Long, epochDay: Long)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: Long)

    @Query("DELETE FROM routine_checks WHERE routineId = :id")
    suspend fun deleteChecks(id: Long)

    @Transaction
    suspend fun delete(id: Long) {
        deleteChecks(id)
        deleteRoutine(id)
    }

    @Query("SELECT * FROM routines")
    suspend fun allRoutines(): List<Routine>

    @Query("SELECT * FROM routine_checks")
    suspend fun allChecks(): List<RoutineCheck>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutines(list: List<Routine>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecks(list: List<RoutineCheck>)

    @Query("DELETE FROM routines")
    suspend fun clearRoutines()

    @Query("DELETE FROM routine_checks")
    suspend fun clearAllChecks()

    @Transaction
    suspend fun toggle(routineId: Long, epochDay: Long) {
        if (isChecked(routineId, epochDay) > 0) uncheck(routineId, epochDay)
        else check(RoutineCheck(routineId, epochDay))
    }
}

// ───────────── 맛집 ─────────────

val RESTAURANT_CATEGORIES = listOf("한식", "중식", "일식", "양식", "아시안", "분식", "고기", "해산물", "카페·디저트", "술집", "기타")
val RESTAURANT_TAGS = listOf("가족", "데이트", "친구", "혼밥", "회식", "접대", "가성비", "분위기", "주차 가능", "아이 동반", "포장·배달")

/** 1인 기준 가격대. Restaurant.price가 이 목록의 위치 (0은 미정) */
val RESTAURANT_PRICES = listOf("미정", "1만원 미만", "1~2만원", "2~3만원", "3~5만원", "5만원 이상")

@Entity(tableName = "restaurants")
data class Restaurant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "",
    /** 주요 메뉴 (쉼표로 구분) */
    val menu: String = "",
    /** 위치 (주소나 동네) */
    val address: String = "",
    /** 지도·블로그 링크 */
    val link: String = "",
    /** 0이면 평가 안 함, 1~5 */
    val rating: Int = 0,
    /** 한 줄 평 */
    val comment: String = "",
    /** 자세한 메모 */
    val memo: String = "",
    /** 쉼표로 구분한 태그 */
    val tags: String = "",
    /** 0 미정, 1 ₩, 2 ₩₩, 3 ₩₩₩ */
    val price: Int = 0,
    /** true면 '가보고 싶은 곳' */
    val wish: Boolean = false,
    val revisit: Boolean = false,
    val visitCount: Int = 0,
    val lastVisitEpochDay: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val tagList: List<String> get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

@Dao
interface RestaurantDao {
    @Query("SELECT * FROM restaurants ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Restaurant>>

    @Insert
    suspend fun insert(r: Restaurant): Long

    @Update
    suspend fun update(r: Restaurant)

    @Delete
    suspend fun delete(r: Restaurant)

    @Query("SELECT * FROM restaurants")
    suspend fun all(): List<Restaurant>

    @Query("SELECT * FROM restaurants WHERE id = :id")
    suspend fun byId(id: Long): Restaurant?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<Restaurant>)

    @Query("DELETE FROM restaurants")
    suspend fun clear()
}

/** 맛집 방문 기록. 한 곳에 여러 번, 날짜마다 따로 남긴다 */
@Entity(tableName = "restaurant_visits")
data class RestaurantVisit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val restaurantId: Long,
    val epochDay: Long,
    /** 그날의 한 줄 메모 */
    val note: String = "",
    /** 사진 파일 이름 (앱 전용 폴더, 없으면 빈 문자열) */
    @ColumnInfo(defaultValue = "''") val photo: String = "",
)

@Dao
interface VisitDao {
    @Query("SELECT * FROM restaurant_visits ORDER BY epochDay DESC, id DESC")
    fun observeAll(): Flow<List<RestaurantVisit>>

    @Query("SELECT * FROM restaurant_visits")
    suspend fun all(): List<RestaurantVisit>

    @Query("SELECT * FROM restaurant_visits WHERE restaurantId = :restaurantId")
    suspend fun of(restaurantId: Long): List<RestaurantVisit>

    @Insert
    suspend fun insert(v: RestaurantVisit): Long

    @Update
    suspend fun update(v: RestaurantVisit)

    @Query("DELETE FROM restaurant_visits WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM restaurant_visits WHERE restaurantId = :restaurantId")
    suspend fun deleteOf(restaurantId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<RestaurantVisit>)

    @Query("DELETE FROM restaurant_visits")
    suspend fun clear()
}

// ───────────── 한 줄 일기 ─────────────

/** 기분 이모지. DiaryEntry.mood는 (목록 위치 + 1), 0이면 선택 안 함 */
val DIARY_MOODS = listOf("😊", "🥰", "😌", "😐", "😔", "😤")

@Entity(tableName = "diary_entries")
data class DiaryEntry(
    /** 날짜마다 하나 */
    @PrimaryKey val epochDay: Long,
    val text: String,
    val mood: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries ORDER BY epochDay DESC")
    fun observeAll(): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries")
    suspend fun all(): List<DiaryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: DiaryEntry)

    @Query("DELETE FROM diary_entries WHERE epochDay = :epochDay")
    suspend fun delete(epochDay: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<DiaryEntry>)

    @Query("DELETE FROM diary_entries")
    suspend fun clear()
}

// ───────────── 장보기 ─────────────

val SHOPPING_CATEGORIES = listOf("채소·과일", "정육·수산", "유제품·달걀", "식료품", "간식·음료", "생활용품", "기타")

@Entity(tableName = "shopping_items")
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 수량·메모 (예: 2개, 500g) */
    val qty: String = "",
    val category: String = "기타",
    val checked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val checkedAt: Long? = null,
)

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY checked ASC, createdAt DESC")
    fun observeAll(): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_items")
    suspend fun all(): List<ShoppingItem>

    @Insert
    suspend fun insert(item: ShoppingItem): Long

    @Update
    suspend fun update(item: ShoppingItem)

    @Delete
    suspend fun delete(item: ShoppingItem)

    @Query("DELETE FROM shopping_items WHERE checked = 1")
    suspend fun clearChecked()

    @Query("UPDATE shopping_items SET checked = 0, checkedAt = NULL")
    suspend fun uncheckAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<ShoppingItem>)

    @Query("DELETE FROM shopping_items")
    suspend fun clear()
}

// ───────────── 독서 ─────────────

enum class BookStatus(val label: String) { READING("읽는 중"), WANT("읽고 싶은"), DONE("다 읽은") }

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "",
    /** 0이면 모름 */
    val totalPages: Int = 0,
    val currentPage: Int = 0,
    /** BookStatus 순서: 0 읽는 중, 1 읽고 싶은, 2 다 읽은 */
    val status: Int = 1,
    /** 0이면 평가 안 함, 1~5 */
    val rating: Int = 0,
    val startEpochDay: Long? = null,
    val endEpochDay: Long? = null,
    /** 다 읽은 뒤 남기는 한 줄 소감 */
    val review: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val statusEnum: BookStatus get() = BookStatus.entries.getOrElse(status) { BookStatus.WANT }
    val progress: Float
        get() = when {
            statusEnum == BookStatus.DONE -> 1f
            totalPages > 0 -> (currentPage.toFloat() / totalPages).coerceIn(0f, 1f)
            else -> 0f
        }
}

/** 책 속 밑줄 문장·메모 */
@Entity(tableName = "book_notes")
data class BookNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val text: String,
    /** 0이면 쪽수 없음 */
    val page: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY createdAt DESC")
    fun observeBooks(): Flow<List<Book>>

    @Query("SELECT * FROM book_notes ORDER BY createdAt DESC")
    fun observeNotes(): Flow<List<BookNote>>

    @Query("SELECT * FROM books")
    suspend fun allBooks(): List<Book>

    @Query("SELECT * FROM book_notes")
    suspend fun allNotes(): List<BookNote>

    @Insert
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBook(id: Long)

    @Query("DELETE FROM book_notes WHERE bookId = :bookId")
    suspend fun deleteNotesOf(bookId: Long)

    @Transaction
    suspend fun delete(id: Long) {
        deleteNotesOf(id)
        deleteBook(id)
    }

    @Insert
    suspend fun insertNote(note: BookNote): Long

    @Query("DELETE FROM book_notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(list: List<Book>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(list: List<BookNote>)

    @Query("DELETE FROM books")
    suspend fun clearBooks()

    @Query("DELETE FROM book_notes")
    suspend fun clearNotes()
}

// ───────────── DB ─────────────

@Database(
    entities = [
        Todo::class, Routine::class, RoutineCheck::class, Restaurant::class,
        DiaryEntry::class, ShoppingItem::class, Book::class, BookNote::class, RestaurantVisit::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun routineDao(): RoutineDao
    abstract fun restaurantDao(): RestaurantDao
    abstract fun visitDao(): VisitDao
    abstract fun diaryDao(): DiaryDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN priority INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE todos ADD COLUMN category TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE todos ADD COLUMN memo TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE todos ADD COLUMN dueMinute INTEGER")
                db.execSQL("ALTER TABLE todos ADD COLUMN remind INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE todos ADD COLUMN completedAt INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `routines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `emoji` TEXT NOT NULL, `color` INTEGER NOT NULL, `days` INTEGER NOT NULL, " +
                        "`reminderEnabled` INTEGER NOT NULL, `reminderMinute` INTEGER NOT NULL, " +
                        "`createdEpochDay` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `routine_checks` (`routineId` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, " +
                        "`checkedAt` INTEGER NOT NULL, PRIMARY KEY(`routineId`, `epochDay`))"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `restaurants` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `category` TEXT NOT NULL, `menu` TEXT NOT NULL, `address` TEXT NOT NULL, " +
                        "`link` TEXT NOT NULL, `rating` INTEGER NOT NULL, `comment` TEXT NOT NULL, `memo` TEXT NOT NULL, " +
                        "`tags` TEXT NOT NULL, `price` INTEGER NOT NULL, `wish` INTEGER NOT NULL, `revisit` INTEGER NOT NULL, " +
                        "`visitCount` INTEGER NOT NULL, `lastVisitEpochDay` INTEGER, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `diary_entries` (`epochDay` INTEGER NOT NULL, `text` TEXT NOT NULL, " +
                        "`mood` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`epochDay`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `shopping_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `qty` TEXT NOT NULL, `category` TEXT NOT NULL, `checked` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `checkedAt` INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `books` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `author` TEXT NOT NULL, `totalPages` INTEGER NOT NULL, `currentPage` INTEGER NOT NULL, " +
                        "`status` INTEGER NOT NULL, `rating` INTEGER NOT NULL, `startEpochDay` INTEGER, `endEpochDay` INTEGER, " +
                        "`review` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `book_notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`bookId` INTEGER NOT NULL, `text` TEXT NOT NULL, `page` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE routines ADD COLUMN kind INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `restaurant_visits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`restaurantId` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, `note` TEXT NOT NULL)"
                )
                // 예전에 '오늘 다녀왔어요'로 남긴 마지막 방문일을 방문 기록으로 옮긴다
                db.execSQL(
                    "INSERT INTO restaurant_visits (restaurantId, epochDay, note) " +
                        "SELECT id, lastVisitEpochDay, '' FROM restaurants WHERE lastVisitEpochDay IS NOT NULL"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE restaurant_visits ADD COLUMN photo TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "dailyhabit.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .addCallback(object : RoomDatabase.Callback() {
                    // PC와 동기화하기 위한 변경 기록 표와 트리거 (앱의 표는 바꾸지 않고, 기존 기록에 uid만 붙인다)
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        com.hdlee73.dailyhabit.sync.SyncSchema.ensure(AndroidSyncDb(db))
                    }
                })
                .build().also { instance = it }
        }
    }
}
