package com.hdlee73.dailyhabit.sync

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 한 기기 안에서 휴대폰 DB와 PC DB를 맞붙여 합치기 규칙을 확인한다 */
class SyncEngineTest {
    private val dir: File = Files.createTempDirectory("synctest").toFile()
    private val devices = ArrayList<TestDevice>()

    private fun phone(name: String = "phone") = TestDevice(dir, name, roomSchema = true).also { devices += it }
    private fun pc(name: String = "pc") = TestDevice(dir, name, roomSchema = false).also { devices += it }

    @AfterTest
    fun cleanup() {
        devices.forEach { it.close() }
        dir.deleteRecursively()
    }

    private fun tick() = Thread.sleep(3)

    private fun fill(d: TestDevice, tag: String) {
        d.addTodo("할 일 $tag")
        val r = d.addRoutine("루틴 $tag")
        d.check(r, 20000); d.check(r, 20001)
        val s = d.addRestaurant("맛집 $tag")
        d.addVisit(s, 20000, "메모 $tag")
        d.diary(20010 + tag.hashCode() % 5L + 5, "일기 $tag")
        val b = d.addBook("책 $tag")
        d.addNote(b, "밑줄 $tag")
        d.addShopping("우유 $tag")
    }

    @Test
    fun upgradeKeepsEveryRecordAndIsRepeatable() {
        val p = phone()
        fill(p, "A")
        p.addTodo("두 번째", done = true)
        val before = p.dump()
        val counts = SyncSchema.tables.associate { it.name to p.count(it.name) }

        p.ensure()
        assertEquals(before, p.dump(), "동기화 표를 만들어도 기존 기록은 그대로")
        for (t in SyncSchema.tables) {
            val live = p.db.query("SELECT COUNT(*) AS n FROM sync_meta WHERE tbl = ? AND deleted = 0", t.name).first().long("n")
            assertEquals(counts[t.name], live, "${t.name}의 모든 기록에 uid가 붙음")
        }
        val seq = SyncSchema.currentSeq(p.db)
        p.ensure() // 앱을 다시 열어도
        assertEquals(before, p.dump())
        assertEquals(seq, SyncSchema.currentSeq(p.db), "두 번째 실행은 아무것도 바꾸지 않음")

        // 트리거가 새 기록·수정·삭제를 남긴다
        val id = p.addTodo("새 할 일")
        val meta = p.db.query("SELECT * FROM sync_meta WHERE tbl = 'todos' AND rowKey = ?", id.toString()).single()
        assertTrue(meta.long("updatedAt") > 1_600_000_000_000L)
        p.deleteTodo(id)
        assertEquals(1L, p.db.query("SELECT deleted FROM sync_meta WHERE tbl = 'todos' AND uid = ?", meta.str("uid")).single().long("deleted"))
    }

    @Test
    fun firstSyncMergesBothSidesWithRelations() {
        val p = phone(); val c = pc()
        fill(p, "폰"); fill(c, "PC")
        p.ensure(); c.ensure()
        val link = DirectLink(p, c)
        link.sync()
        assertEquals(p.dump(), c.dump())
        assertEquals(2, p.count("todos")); assertEquals(4, p.count("routine_checks")); assertEquals(2, c.count("book_notes"))
        // 부모 관계가 맞는지 (체크가 제 루틴에, 밑줄이 제 책에)
        val rid = c.routineId("루틴 폰")!!
        assertEquals(2, c.db.query("SELECT COUNT(*) AS n FROM routine_checks WHERE routineId = ?", rid).first().long("n"))
        // 다시 해도 주고받을 것이 없다
        val again = link.sync()
        assertEquals(0, again.sent); assertEquals(0, again.received)
        assertEquals(p.dump(), c.dump())
    }

    @Test
    fun sameBackupOnBothDevicesDoesNotDuplicate() {
        val p = phone(); val c = pc()
        // 예전에 휴대폰 백업 파일을 PC에서 불러온 경우: 같은 id·같은 만든 시각
        for (d in listOf(p, c)) {
            d.addTodo("같은 할 일", createdAt = 111)
            val r = d.addRoutine("같은 루틴"); d.check(r, 20000)
            val s = d.addRestaurant("같은 맛집", createdAt = 222); d.addVisit(s, 20000, "좋음")
            d.diary(20000, "같은 일기")
            val b = d.addBook("같은 책", createdAt = 333); d.addNote(b, "같은 밑줄", createdAt = 444)
            d.addRoutine("오늘의 말씀 읽기", kind = 1)
        }
        // PC에서만 나중에 하나 더
        c.addTodo("PC 할 일", createdAt = 555)
        p.ensure(); c.ensure()
        DirectLink(p, c).sync()
        assertEquals(p.dump(), c.dump())
        assertEquals(2, p.count("todos")); assertEquals(2, p.count("routines")); assertEquals(1, p.count("routine_checks"))
        assertEquals(1, p.count("restaurant_visits")); assertEquals(1, p.count("diary_entries")); assertEquals(1, p.count("book_notes"))
    }

    @Test
    fun faithRoutinesSeededSeparatelyBecomeOne() {
        val p = phone(); val c = pc()
        p.ensure(); c.ensure()
        // 동기화 기능이 생긴 뒤 두 기기가 각자 말씀·기도 루틴을 만들고 체크
        val pr = p.addRoutine("오늘의 말씀 읽기", kind = 1); p.check(pr, 20100)
        tick()
        val cr = c.addRoutine("오늘의 말씀 읽기", kind = 1); c.check(cr, 20100); c.check(cr, 20101)
        DirectLink(p, c).sync()
        assertEquals(1, p.count("routines")); assertEquals(1, c.count("routines"))
        assertEquals(2, p.count("routine_checks"))
        assertEquals(p.dump(), c.dump())
    }

    @Test
    fun laterEditWinsAndDeletesTravel() {
        val p = phone(); val c = pc()
        p.ensure(); c.ensure()
        val link = DirectLink(p, c)
        p.addTodo("장보기"); p.addTodo("운동"); p.addTodo("청소")
        link.sync()

        // 같은 할 일을 양쪽에서 고침: PC가 나중
        p.setTodoTitle(p.todoId("장보기")!!, "장보기 (폰)"); tick()
        c.setTodoTitle(c.todoId("장보기")!!, "장보기 (PC)")
        // 폰에서 지운 것, PC에서 지운 것
        p.deleteTodo(p.todoId("운동")!!)
        c.deleteTodo(c.todoId("청소")!!)
        link.sync()
        assertEquals(listOf("장보기 (PC)"), p.db.query("SELECT title FROM todos").map { it.str("title") })
        assertEquals(p.dump(), c.dump())

        // 지운 뒤 다른 쪽에서 나중에 고치면 되살아나고, 고친 뒤 나중에 지우면 지워진다
        val id = c.todoId("장보기 (PC)")!!
        p.deleteTodo(p.todoId("장보기 (PC)")!!); tick()
        c.setTodoTitle(id, "다시 살림")
        link.sync()
        assertNotNull(p.todoId("다시 살림"))
        c.setTodoTitle(id, "고침"); tick()
        p.deleteTodo(p.todoId("다시 살림")!!)
        link.sync()
        assertEquals(0, c.count("todos"))
        assertEquals(p.dump(), c.dump())
    }

    @Test
    fun routineChecksAndDiaryFollowTheLatest() {
        val p = phone(); val c = pc()
        p.ensure(); c.ensure()
        val link = DirectLink(p, c)
        val r = p.addRoutine("물 마시기")
        link.sync()
        val cr = c.routineId("물 마시기")!!

        p.check(r, 20200)
        link.sync()
        assertEquals(1, c.count("routine_checks"))
        c.uncheck(cr, 20200)
        link.sync()
        assertEquals(0, p.count("routine_checks"))
        p.check(r, 20200) // 다시 체크
        link.sync()
        assertEquals(1, c.count("routine_checks"))

        // 같은 날 일기를 양쪽에서 씀: 나중 것 하나만
        p.diary(20300, "폰에서 쓴 일기"); tick()
        c.diary(20300, "PC에서 쓴 일기")
        link.sync()
        assertEquals(listOf("PC에서 쓴 일기"), p.db.query("SELECT text FROM diary_entries").map { it.str("text") })
        assertEquals(p.dump(), c.dump())

        // 루틴을 지우면 체크도 함께 (앱처럼 체크를 먼저 지움)
        c.db.exec("DELETE FROM routine_checks WHERE routineId = ?", cr)
        c.db.exec("DELETE FROM routines WHERE id = ?", cr)
        link.sync()
        assertEquals(0, p.count("routines")); assertEquals(0, p.count("routine_checks"))
    }

    @Test
    fun visitsKeepRestaurantCountsRight() {
        val p = phone(); val c = pc()
        p.ensure(); c.ensure()
        val link = DirectLink(p, c)
        val s = p.addRestaurant("국밥집")
        link.sync()
        val cs = c.db.query("SELECT id FROM restaurants").single().long("id")
        p.addVisit(s, 20400, "폰 방문"); tick()
        c.addVisit(cs, 20401, "PC 방문")
        link.sync()
        for (d in listOf(p, c)) {
            val row = d.db.query("SELECT visitCount, lastVisitEpochDay FROM restaurants").single()
            assertEquals(2L, row.long("visitCount"), "양쪽 방문이 합쳐진 횟수")
            assertEquals(20401L, row.long("lastVisitEpochDay"))
        }
        assertEquals(2, p.count("restaurant_visits"))
    }

    @Test
    fun twoPhonesShareThroughThePc() {
        val p1 = phone("p1"); val p2 = phone("p2"); val c = pc()
        listOf(p1, p2, c).forEach { it.ensure() }
        val l1 = DirectLink(p1, c); val l2 = DirectLink(p2, c)
        p1.addTodo("폰1"); p2.addTodo("폰2")
        l1.sync(); l2.sync(); l1.sync()
        assertEquals(p1.dump(), p2.dump())
        assertEquals(p1.dump(), c.dump())
        p2.deleteTodo(p2.todoId("폰1")!!)
        l2.sync(); l1.sync()
        assertNull(p1.todoId("폰1"))
    }

    @Test
    fun restoreThenSyncKeepsIdentity() {
        val p = phone(); val c = pc()
        fill(p, "폰")
        p.ensure(); c.ensure()
        val link = DirectLink(p, c)
        link.sync()
        // 휴대폰 백업 파일(uid 포함)을 다른 PC에 되돌림
        val uids = SyncSchema.exportUids(p.db)
        val c2 = pc("pc2")
        c2.ensure()
        c2.addTodo("PC2에만 있던 것") // 되돌리면 사라질 기록
        for (t in SyncSchema.tables) {
            c2.db.exec("DELETE FROM ${t.name}")
            val rows = SyncSchema.selectAll(p.db, t)
            for (row in rows) {
                val cols = row.keys.toList()
                c2.db.exec("INSERT OR REPLACE INTO ${t.name} (${cols.joinToString(", ")}) VALUES (${cols.joinToString(", ") { "?" }})", *cols.map { row[it] }.toTypedArray())
            }
        }
        SyncSchema.afterRestore(c2.db, uids)
        assertEquals(p.dump(), c2.dump())
        val link2 = DirectLink(p, c2).apply { state = SyncEngine.ClientState.FRESH }
        link2.sync()
        assertEquals(p.dump(), c2.dump(), "같은 uid라 둘로 늘지 않음")
        assertEquals(1, p.count("todos"))

        // uid가 없는 예전 백업(v1.7.0)을 되돌려도 내용으로 같은 기록을 알아본다
        val c3 = pc("pc3")
        c3.ensure()
        for (t in SyncSchema.tables) {
            for (row in SyncSchema.selectAll(p.db, t)) {
                val cols = row.keys.toList()
                c3.db.exec("INSERT OR REPLACE INTO ${t.name} (${cols.joinToString(", ")}) VALUES (${cols.joinToString(", ") { "?" }})", *cols.map { row[it] }.toTypedArray())
            }
        }
        SyncSchema.afterRestore(c3.db, emptyMap())
        val p3 = phone("p3")
        for (t in SyncSchema.tables) {
            for (row in SyncSchema.selectAll(p.db, t)) {
                val cols = row.keys.toList()
                p3.db.exec("INSERT OR REPLACE INTO ${t.name} (${cols.joinToString(", ")}) VALUES (${cols.joinToString(", ") { "?" }})", *cols.map { row[it] }.toTypedArray())
            }
        }
        p3.ensure()
        DirectLink(p3, c3).sync()
        assertEquals(p.dump(), p3.dump())
        assertEquals(p.dump(), c3.dump())
    }

    @Test
    fun manyRecordsStayConsistent() {
        val p = phone(); val c = pc()
        p.ensure(); c.ensure()
        val link = DirectLink(p, c)
        val rnd = java.util.Random(7)
        repeat(8) { round ->
            repeat(30) { i ->
                val d = if (rnd.nextBoolean()) p else c
                when (rnd.nextInt(6)) {
                    0 -> d.addTodo("t$round-$i")
                    1 -> d.db.query("SELECT id FROM todos").randomOrNull()?.let { d.setTodoTitle(it.long("id"), "edit$round-$i") }
                    2 -> d.db.query("SELECT id FROM todos").randomOrNull()?.let { d.deleteTodo(it.long("id")) }
                    3 -> d.db.query("SELECT id FROM routines").randomOrNull()?.let { d.check(it.long("id"), 20000L + rnd.nextInt(5)) } ?: d.addRoutine("r$round-$i")
                    4 -> d.diary(20000L + rnd.nextInt(10), "d$round-$i")
                    else -> d.addBook("b$round-$i").let { d.addNote(it, "n$round-$i") }
                }
            }
            link.sync()
            assertEquals(p.dump(), c.dump(), "round $round")
        }
    }
}
