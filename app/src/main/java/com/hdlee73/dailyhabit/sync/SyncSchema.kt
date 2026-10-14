package com.hdlee73.dailyhabit.sync

import java.security.MessageDigest
import java.util.UUID

/**
 * 동기화용 변경 기록.
 *
 * 앱의 기존 표는 그대로 두고, 표마다 sync_meta에 한 줄씩 (고유 번호 uid, 수정 시각, 삭제 표시, 순번)을 둔다.
 * 기록이 바뀌면 SQLite 트리거가 sync_meta를 고치므로 화면·DAO 코드는 손대지 않아도 된다.
 * 기기마다 다른 id(자동 번호) 대신 uid로 같은 기록을 알아본다.
 */
object SyncSchema {

    enum class Key { AUTO, DIARY, CHECK }

    class Col(val name: String, val default: Any?)

    class Table(
        val name: String,
        val key: Key,
        val cols: List<Col>,
        /** 부모 기록을 가리키는 열과 부모 표 (전송할 때는 부모의 uid로 바꾼다) */
        val fk: Pair<String, String>? = null,
    ) {
        val colNames = cols.map { it.name }
    }

    private fun c(name: String, default: Any?) = Col(name, default)

    /** 부모 표가 자식 표보다 앞에 온다 */
    val tables: List<Table> = listOf(
        Table(
            "routines", Key.AUTO,
            listOf(
                c("title", ""), c("emoji", "✨"), c("color", 0L), c("days", 127L), c("reminderEnabled", 0L),
                c("reminderMinute", 480L), c("createdEpochDay", 0L), c("archived", 0L), c("sortOrder", 0L), c("kind", 0L),
            ),
        ),
        Table("routine_checks", Key.CHECK, listOf(c("routineId", null), c("epochDay", 0L), c("checkedAt", 0L)), fk = "routineId" to "routines"),
        Table(
            "todos", Key.AUTO,
            listOf(
                c("title", ""), c("done", 0L), c("dueEpochDay", null), c("createdAt", 0L), c("priority", 1L), c("category", ""),
                c("memo", ""), c("dueMinute", null), c("remind", 0L), c("completedAt", null),
            ),
        ),
        Table(
            "restaurants", Key.AUTO,
            listOf(
                c("name", ""), c("category", ""), c("menu", ""), c("address", ""), c("link", ""), c("rating", 0L),
                c("comment", ""), c("memo", ""), c("tags", ""), c("price", 0L), c("wish", 0L), c("revisit", 0L),
                c("visitCount", 0L), c("lastVisitEpochDay", null), c("createdAt", 0L),
            ),
        ),
        Table(
            "restaurant_visits", Key.AUTO,
            listOf(c("restaurantId", null), c("epochDay", 0L), c("note", ""), c("photo", "")),
            fk = "restaurantId" to "restaurants",
        ),
        Table("diary_entries", Key.DIARY, listOf(c("epochDay", 0L), c("text", ""), c("mood", 0L), c("updatedAt", 0L))),
        Table(
            "shopping_items", Key.AUTO,
            listOf(c("name", ""), c("qty", ""), c("category", "기타"), c("checked", 0L), c("createdAt", 0L), c("checkedAt", null)),
        ),
        Table(
            "books", Key.AUTO,
            listOf(
                c("title", ""), c("author", ""), c("totalPages", 0L), c("currentPage", 0L), c("status", 1L), c("rating", 0L),
                c("startEpochDay", null), c("endEpochDay", null), c("review", ""), c("createdAt", 0L),
            ),
        ),
        Table("book_notes", Key.AUTO, listOf(c("bookId", null), c("text", ""), c("page", 0L), c("createdAt", 0L)), fk = "bookId" to "books"),
    )

    val byName: Map<String, Table> = tables.associateBy { it.name }

    /** 백업 파일에서 쓰는 이름 → 표 이름 */
    val backupNames: Map<String, String> = mapOf(
        "todos" to "todos", "routines" to "routines", "routineChecks" to "routine_checks", "restaurants" to "restaurants",
        "restaurantVisits" to "restaurant_visits", "diary" to "diary_entries", "shopping" to "shopping_items",
        "books" to "books", "bookNotes" to "book_notes",
    )

    // ───────────── 표 만들기 ─────────────

    private const val NOW = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"
    private const val NOT_MUTED = "(SELECT mute FROM sync_ctl WHERE id = 1) = 0"
    private const val CUR_SEQ = "(SELECT seq FROM sync_ctl WHERE id = 1)"
    private const val RANDOM_UID = "lower(hex(randomblob(16)))"

    private fun keyExpr(t: Table, p: String) = when (t.key) {
        Key.AUTO -> "CAST($p.id AS TEXT)"
        Key.DIARY -> "CAST($p.epochDay AS TEXT)"
        Key.CHECK -> "CAST($p.routineId AS TEXT) || ':' || CAST($p.epochDay AS TEXT)"
    }

    /** 두 기기에서 따로 만들어도 같은 기록이어야 하는 것은 정해진 uid를 쓴다 (없으면 NULL → 무작위) */
    private fun fixedUidExpr(t: Table): String = when (t.name) {
        "routines" -> "CASE WHEN NEW.kind <> 0 THEN 'rk' || NEW.kind ELSE NULL END"
        "routine_checks" -> "(SELECT uid FROM sync_meta WHERE tbl = 'routines' AND rowKey = CAST(NEW.routineId AS TEXT)) || ':' || NEW.epochDay"
        "diary_entries" -> "'diary:' || NEW.epochDay"
        else -> "NULL"
    }

    private fun upsertTriggerBody(t: Table): String {
        val k = keyExpr(t, "NEW")
        val n = t.name
        val noRow = "NOT EXISTS (SELECT 1 FROM sync_meta WHERE tbl = '$n' AND rowKey = $k)"
        val d = fixedUidExpr(t)
        // 트리거 안의 ON CONFLICT는 바깥 문장(INSERT OR REPLACE 등)의 것으로 바뀌므로 충돌이 아예 안 나게 조건으로 막는다
        return """
            UPDATE sync_ctl SET seq = seq + 1 WHERE id = 1;
            UPDATE sync_meta SET rowKey = $k WHERE tbl = '$n' AND uid = $d AND rowKey IS NULL AND $noRow;
            INSERT INTO sync_meta (tbl, uid, rowKey, updatedAt, deleted, seq) SELECT '$n', $d, $k, 0, 0, 0
                WHERE $noRow AND $d IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sync_meta WHERE tbl = '$n' AND uid = $d);
            INSERT INTO sync_meta (tbl, uid, rowKey, updatedAt, deleted, seq) SELECT '$n', $RANDOM_UID, $k, 0, 0, 0 WHERE $noRow;
            UPDATE sync_meta SET updatedAt = $NOW, deleted = 0, seq = $CUR_SEQ WHERE tbl = '$n' AND rowKey = $k;
        """.trimIndent()
    }

    /**
     * 동기화 표와 트리거를 만들고, 아직 변경 기록이 없는 기존 기록에 uid를 붙인다.
     * 앱을 열 때마다 불러도 된다 (기존 기록은 절대 지우거나 바꾸지 않는다).
     */
    fun ensure(db: SyncDb) = db.tx {
        db.exec(
            "CREATE TABLE IF NOT EXISTS sync_meta (tbl TEXT NOT NULL, uid TEXT NOT NULL, rowKey TEXT, " +
                "updatedAt INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0, seq INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (tbl, uid))"
        )
        db.exec("CREATE INDEX IF NOT EXISTS sync_meta_row ON sync_meta (tbl, rowKey)")
        db.exec("CREATE INDEX IF NOT EXISTS sync_meta_seq ON sync_meta (seq)")
        db.exec("CREATE TABLE IF NOT EXISTS sync_ctl (id INTEGER PRIMARY KEY, mute INTEGER NOT NULL DEFAULT 0, seq INTEGER NOT NULL DEFAULT 0)")
        db.exec("INSERT OR IGNORE INTO sync_ctl (id, mute, seq) VALUES (1, 0, 0)")
        // 지난번에 앱이 동기화 도중 꺼졌더라도 트랜잭션이 되돌려지므로 mute는 0이지만, 혹시 몰라 다시 맞춘다
        db.exec("UPDATE sync_ctl SET mute = 0 WHERE id = 1")
        for (t in tables) {
            for (s in listOf("ai", "au", "ad")) db.exec("DROP TRIGGER IF EXISTS sync_${t.name}_$s")
            db.exec("CREATE TRIGGER sync_${t.name}_ai AFTER INSERT ON ${t.name} WHEN $NOT_MUTED BEGIN\n${upsertTriggerBody(t)}\nEND")
            db.exec("CREATE TRIGGER sync_${t.name}_au AFTER UPDATE ON ${t.name} WHEN $NOT_MUTED BEGIN\n${upsertTriggerBody(t)}\nEND")
            db.exec(
                "CREATE TRIGGER sync_${t.name}_ad AFTER DELETE ON ${t.name} WHEN $NOT_MUTED BEGIN\n" +
                    "UPDATE sync_ctl SET seq = seq + 1 WHERE id = 1;\n" +
                    "UPDATE sync_meta SET deleted = 1, updatedAt = $NOW, seq = $CUR_SEQ WHERE tbl = '${t.name}' AND rowKey = ${keyExpr(t, "OLD")};\n" +
                    "END"
            )
        }
        backfill(db)
    }

    // ───────────── 기록 읽기 도우미 ─────────────

    fun rowKey(t: Table, row: Map<String, Any?>): String = when (t.key) {
        Key.AUTO -> row.long("id").toString()
        Key.DIARY -> row.long("epochDay").toString()
        Key.CHECK -> "${row.long("routineId")}:${row.long("epochDay")}"
    }

    fun selectAll(db: SyncDb, t: Table): List<Map<String, Any?>> {
        val cols = (if (t.key == Key.AUTO) listOf("id") else emptyList()) + t.colNames
        return db.query("SELECT ${cols.joinToString(", ")} FROM ${t.name}")
    }

    fun nextSeq(db: SyncDb): Long {
        db.exec("UPDATE sync_ctl SET seq = seq + 1 WHERE id = 1")
        return db.query("SELECT seq FROM sync_ctl WHERE id = 1").first().long("seq")
    }

    fun currentSeq(db: SyncDb): Long = db.query("SELECT seq FROM sync_ctl WHERE id = 1").first().long("seq")

    fun setMute(db: SyncDb, mute: Boolean) = db.exec("UPDATE sync_ctl SET mute = ? WHERE id = 1", if (mute) 1L else 0L)

    fun newUid(): String = UUID.randomUUID().toString().replace("-", "")

    private fun sha(vararg parts: Any?): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(parts.joinToString("\u0001") { it?.toString() ?: "\u0000" }.toByteArray(Charsets.UTF_8))
        return "c" + bytes.take(15).joinToString("") { "%02x".format(it) }
    }

    /**
     * 기존 기록(동기화 기능 이전에 만든 것)에 붙일 uid. 내용으로 정해지므로 백업 파일로 옮긴 같은 기록은
     * 두 기기에서 같은 uid가 되어, 처음 동기화할 때 둘로 늘어나지 않는다.
     * [strong]이면 겹칠 때 쓸 더 자세한 값.
     */
    fun contentUid(t: Table, row: Map<String, Any?>, parentUid: String?, strong: Boolean): String? = when (t.name) {
        "routines" -> if ((row.longOrNull("kind") ?: 0L) != 0L) "rk${row.long("kind")}"
        else sha(t.name, row["title"], row["createdEpochDay"], if (strong) row["emoji"] else "")
        "routine_checks" -> parentUid?.let { "$it:${row.long("epochDay")}" }
        "diary_entries" -> "diary:${row.long("epochDay")}"
        "restaurant_visits" -> parentUid?.let { sha(t.name, it, row["epochDay"], if (strong) row["note"] else "") }
        "book_notes" -> parentUid?.let { sha(t.name, it, row["createdAt"], if (strong) row["text"] else "") }
        "todos", "books" -> sha(t.name, row["createdAt"], if (strong) row["title"] else "")
        "restaurants", "shopping_items" -> sha(t.name, row["createdAt"], if (strong) row["name"] else "")
        else -> null
    }

    /** 표의 rowKey → uid (살아 있는 것만) */
    fun liveUids(db: SyncDb, table: String): Map<String, String> =
        db.query("SELECT rowKey, uid FROM sync_meta WHERE tbl = ? AND deleted = 0 AND rowKey IS NOT NULL", table)
            .associate { it.str("rowKey")!! to it.str("uid")!! }

    fun parentUidOf(t: Table, row: Map<String, Any?>, parentUids: Map<String, Map<String, String>>): String? {
        val (col, parent) = t.fk ?: return null
        return parentUids[parent]?.get(row.longOrNull(col)?.toString())
    }

    /** 표의 기록마다 정할 uid. [given]이 있으면 그것을 먼저 쓴다 (백업 파일의 uid) */
    private fun chooseUids(
        t: Table,
        rows: List<Map<String, Any?>>,
        parentUids: Map<String, Map<String, String>>,
        taken: MutableSet<String>,
        given: (String) -> String? = { null },
    ): Map<String, String> {
        val out = HashMap<String, String>()
        val weak = rows.associateWith { contentUid(t, it, parentUidOf(t, it, parentUids), strong = false) }
        val weakCount = weak.values.filterNotNull().groupingBy { it }.eachCount()
        for (row in rows) {
            val key = rowKey(t, row)
            val fixed = given(key)?.takeIf { it !in taken }
            val uid = fixed ?: run {
                val w = weak[row]
                val c = if (w != null && (weakCount[w] ?: 0) > 1) contentUid(t, row, parentUidOf(t, row, parentUids), strong = true) else w
                c?.takeIf { it !in taken } ?: newUid()
            }
            taken += uid
            out[key] = uid
        }
        return out
    }

    /** 변경 기록이 없는 기록에 uid를 붙이고, 사라진 기록은 삭제 표시를 한다 */
    private fun backfill(db: SyncDb) {
        val parentUids = HashMap<String, Map<String, String>>()
        val now = System.currentTimeMillis()
        for (t in tables) {
            val rows = selectAll(db, t)
            val metas = db.query("SELECT uid, rowKey, deleted FROM sync_meta WHERE tbl = ?", t.name)
            val known = metas.mapNotNull { m -> m.str("rowKey")?.takeIf { m.long("deleted") == 0L } }.toSet()
            val keys = rows.map { rowKey(t, it) }.toSet()
            val missing = rows.filter { rowKey(t, it) !in known }
            if (missing.isNotEmpty()) {
                val taken = metas.filter { it.long("deleted") == 0L }.map { it.str("uid")!! }.toMutableSet()
                val uids = chooseUids(t, missing, parentUids, taken)
                for (row in missing) {
                    val key = rowKey(t, row)
                    // 동기화 전의 기록은 수정 시각을 모르므로 1 (일기는 저장된 시각)로 둔다
                    val at = if (t.key == Key.DIARY) row.longOrNull("updatedAt") ?: 1L else 1L
                    db.exec("UPDATE sync_meta SET rowKey = NULL WHERE tbl = ? AND rowKey = ?", t.name, key)
                    db.exec(
                        "INSERT OR REPLACE INTO sync_meta (tbl, uid, rowKey, updatedAt, deleted, seq) VALUES (?, ?, ?, ?, 0, ?)",
                        t.name, uids[key], key, at, nextSeq(db),
                    )
                }
            }
            for (m in metas) {
                val k = m.str("rowKey") ?: continue
                if (m.long("deleted") == 0L && k !in keys) {
                    db.exec(
                        "UPDATE sync_meta SET deleted = 1, updatedAt = ?, seq = ? WHERE tbl = ? AND uid = ?",
                        now, nextSeq(db), t.name, m.str("uid"),
                    )
                }
            }
            parentUids[t.name] = liveUids(db, t.name)
        }
    }

    // ───────────── 백업 파일 ─────────────

    /** 백업 파일에 넣을 uid 목록: 백업 이름 → (rowKey → uid) */
    fun exportUids(db: SyncDb): Map<String, Map<String, String>> = db.tx {
        backupNames.mapValues { (_, table) -> liveUids(db, table) }
    }

    /**
     * 백업을 되돌린 뒤 부른다. 되돌린 기록마다 백업 파일의 uid(없으면 내용으로 정한 uid)를 붙이고
     * 지금 바꾼 것으로 표시해 다음 동기화 때 다른 기기에도 반영되게 한다.
     */
    fun afterRestore(db: SyncDb, backupUids: Map<String, Map<String, String>>) = db.tx {
        val byTable = backupUids.mapKeys { backupNames[it.key] ?: it.key }
        val now = System.currentTimeMillis()
        setMute(db, true)
        try {
            val parentUids = HashMap<String, Map<String, String>>()
            for (t in tables) {
                val rows = selectAll(db, t)
                val given = byTable[t.name].orEmpty()
                val metas = db.query("SELECT uid, rowKey, deleted FROM sync_meta WHERE tbl = ?", t.name)
                // 정해진 uid 규칙(일기·체크·말씀 루틴)은 백업보다 앞선다
                val fixedRule = t.name == "routine_checks" || t.name == "diary_entries"
                val uids = chooseUids(t, rows, parentUids, HashSet(), given = { k -> if (fixedRule) null else given[k] })
                val keys = rows.map { rowKey(t, it) }.toSet()
                // 되돌린 뒤 없는 기록은 삭제로 표시
                for (m in metas) {
                    val k = m.str("rowKey")
                    if (m.long("deleted") == 0L && (k == null || k !in keys)) {
                        db.exec("UPDATE sync_meta SET deleted = 1, rowKey = NULL, updatedAt = ?, seq = ? WHERE tbl = ? AND uid = ?", now, nextSeq(db), t.name, m.str("uid"))
                    }
                }
                for (row in rows) {
                    val key = rowKey(t, row)
                    val uid = uids[key]!!
                    // 이 자리에 있던 다른 uid는 삭제로 남긴다 (다른 기기에서도 지워지도록)
                    db.exec(
                        "UPDATE sync_meta SET deleted = 1, rowKey = NULL, updatedAt = ?, seq = ? WHERE tbl = ? AND rowKey = ? AND uid <> ?",
                        now, nextSeq(db), t.name, key, uid,
                    )
                    val exists = db.query("SELECT 1 AS x FROM sync_meta WHERE tbl = ? AND uid = ?", t.name, uid).isNotEmpty()
                    if (exists) {
                        db.exec("UPDATE sync_meta SET rowKey = ?, deleted = 0, updatedAt = ?, seq = ? WHERE tbl = ? AND uid = ?", key, now, nextSeq(db), t.name, uid)
                    } else {
                        db.exec("INSERT INTO sync_meta (tbl, uid, rowKey, updatedAt, deleted, seq) VALUES (?, ?, ?, ?, 0, ?)", t.name, uid, key, now, nextSeq(db))
                    }
                }
                parentUids[t.name] = liveUids(db, t.name)
            }
        } finally {
            setMute(db, false)
        }
    }
}
