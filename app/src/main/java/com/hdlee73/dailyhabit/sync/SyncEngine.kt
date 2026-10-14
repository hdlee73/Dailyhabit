package com.hdlee73.dailyhabit.sync

import com.hdlee73.dailyhabit.sync.SyncSchema.Key
import com.hdlee73.dailyhabit.sync.SyncSchema.Table
import org.json.JSONArray
import org.json.JSONObject

/**
 * 변경 기록을 모으고 합친다.
 *
 * 변경 하나 = {"t": 표, "u": uid, "at": 수정 시각(ms), "d": 1이면 삭제, "v": 열 값}.
 * 같은 기록을 양쪽에서 고쳤으면 수정 시각이 늦은 쪽이 이긴다 (같으면 정해진 규칙으로 양쪽이 같은 쪽을 고른다).
 */
object SyncEngine {

    data class Change(val table: String, val uid: String, val at: Long, val deleted: Boolean, val values: JSONObject?) {
        val id get() = "$table|$uid"

        fun toJson(): JSONObject = JSONObject().put("t", table).put("u", uid).put("at", at).put("d", if (deleted) 1 else 0)
            .also { if (values != null) it.put("v", values) }

        companion object {
            fun fromJson(o: JSONObject) = Change(
                o.getString("t"), o.getString("u"), o.getLong("at"), o.optInt("d", 0) == 1, o.optJSONObject("v"),
            )
        }
    }

    class ApplyResult {
        /** 받은 쪽이 저장한(또는 이미 같았던) 변경 */
        val applied = HashSet<String>()
        /** 이쪽 기록이 더 새로워서 버린 변경 */
        val rejected = HashSet<String>()
        val changedTables = HashSet<String>()
        var skippedOrphans = 0
    }

    private fun canonical(v: JSONObject?): String {
        if (v == null) return ""
        return v.keys().asSequence().sorted().joinToString(";") { k -> "$k=" + (if (v.isNull(k)) "null" else v.get(k).toString()) }
    }

    /** 기록 한 줄을 전송용 값으로 (부모 id는 부모 uid로) */
    private fun valuesOf(t: Table, row: Map<String, Any?>, parentUids: Map<String, String>?): JSONObject? {
        val o = JSONObject()
        for (c in t.cols) {
            val v = row[c.name]
            if (t.fk?.first == c.name) {
                val pu = parentUids?.get((v as? Number)?.toLong()?.toString()) ?: return null
                o.put(c.name, pu)
            } else {
                o.put(c.name, v ?: JSONObject.NULL)
            }
        }
        return o
    }

    private class Snapshot(val db: SyncDb) {
        private val rows = HashMap<String, Map<String, Map<String, Any?>>>()
        private val uids = HashMap<String, Map<String, String>>()
        fun rows(t: Table) = rows.getOrPut(t.name) { SyncSchema.selectAll(db, t).associateBy { SyncSchema.rowKey(t, it) } }
        fun uids(table: String) = uids.getOrPut(table) { SyncSchema.liveUids(db, table) }
        fun values(t: Table, rowKey: String?): JSONObject? {
            val row = rowKey?.let { rows(t)[it] } ?: return null
            return valuesOf(t, row, t.fk?.let { uids(it.second) })
        }
    }

    private fun changeOf(snap: Snapshot, m: Map<String, Any?>): Change? {
        val t = SyncSchema.byName[m.str("tbl")] ?: return null
        val deleted = m.long("deleted") == 1L
        val values = if (deleted) null else snap.values(t, m.str("rowKey")) ?: return null
        return Change(t.name, m.str("uid")!!, m.long("updatedAt"), deleted, values)
    }

    /** [since]보다 순번이 큰 변경 (since < 0이면 전부) */
    fun collect(db: SyncDb, since: Long): List<Change> = db.tx {
        val snap = Snapshot(db)
        val metas = if (since < 0) db.query("SELECT * FROM sync_meta ORDER BY seq")
        else db.query("SELECT * FROM sync_meta WHERE seq > ? ORDER BY seq", since)
        metas.mapNotNull { changeOf(snap, it) }
    }

    private fun collectIds(db: SyncDb, ids: Collection<String>): List<Change> {
        val snap = Snapshot(db)
        return ids.mapNotNull { id ->
            val (t, u) = id.split('|', limit = 2)
            db.query("SELECT * FROM sync_meta WHERE tbl = ? AND uid = ?", t, u).firstOrNull()?.let { changeOf(snap, it) }
        }
    }

    /** remote가 local을 이기는지. 양쪽이 같은 답을 내도록 시각 → 삭제 여부 → 내용 순으로 비교 */
    private fun remoteWins(remote: Change, localAt: Long, localDeleted: Boolean, localValues: JSONObject?): Boolean {
        if (remote.at != localAt) return remote.at > localAt
        if (remote.deleted != localDeleted) return remote.deleted
        return canonical(remote.values) > canonical(localValues)
    }

    private fun bindValue(v: Any?): Any? = when (v) {
        null, JSONObject.NULL -> null
        is Boolean -> if (v) 1L else 0L
        is Int -> v.toLong()
        is Long -> v
        is Number -> if (v.toDouble() == Math.floor(v.toDouble())) v.toLong() else v.toDouble()
        else -> v.toString()
    }

    /**
     * 받은 변경을 저장한다. [assignSeq]이면 저장한 변경에 새 순번을 붙인다 (PC 쪽: 다른 휴대폰에도 전하려고).
     * 휴대폰 쪽은 받은 것을 다시 보내지 않도록 순번 0으로 둔다.
     */
    fun apply(db: SyncDb, changes: List<Change>, assignSeq: Boolean): ApplyResult = db.tx {
        val result = ApplyResult()
        val order = SyncSchema.tables.withIndex().associate { it.value.name to it.index }
        val sorted = changes.filter { it.table in order }
            .sortedWith(compareBy<Change>({ if (it.deleted) 1 else 0 }, { order[it.table] }))
        val touchedRestaurants = HashSet<Long>()
        fun seq(): Long = if (assignSeq) SyncSchema.nextSeq(db) else 0L

        SyncSchema.setMute(db, true)
        try {
            for (c in sorted) {
                val t = SyncSchema.byName.getValue(c.table)
                val local = db.query("SELECT * FROM sync_meta WHERE tbl = ? AND uid = ?", t.name, c.uid).firstOrNull()
                val localKey = local?.str("rowKey")
                val localRow = localKey?.let { k -> rowByKey(db, t, k) }
                if (local != null) {
                    val localDeleted = local.long("deleted") == 1L
                    val localAt = local.long("updatedAt")
                    if (c.at < localAt) { result.rejected += c.id; continue }
                    if (c.at == localAt) {
                        // 시각이 같을 때만 내용을 비교한다
                        val localValues = if (localDeleted) null else localRow?.let { valuesOf(t, it, t.fk?.let { f -> SyncSchema.liveUids(db, f.second) }) }
                        if (c.deleted == localDeleted && canonical(c.values) == canonical(localValues)) { result.applied += c.id; continue }
                        if (!remoteWins(c, localAt, localDeleted, localValues)) { result.rejected += c.id; continue }
                    }
                }
                if (t.name == "restaurant_visits") localRow?.longOrNull("restaurantId")?.let { touchedRestaurants += it }

                if (c.deleted) {
                    if (localRow != null) deleteRow(db, t, localKey)
                    upsertMeta(db, t.name, c.uid, null, c.at, true, seq())
                } else {
                    val vals = c.values ?: JSONObject()
                    val args = ArrayList<Any?>()
                    var orphan = false
                    for (col in t.cols) {
                        val raw = if (vals.has(col.name)) vals.get(col.name) else col.default
                        if (t.fk?.first == col.name) {
                            val parentKey = db.query(
                                "SELECT rowKey FROM sync_meta WHERE tbl = ? AND uid = ? AND deleted = 0 AND rowKey IS NOT NULL",
                                t.fk.second, raw?.toString(),
                            ).firstOrNull()?.str("rowKey")
                            if (parentKey == null) { orphan = true; break }
                            args += parentKey.toLong()
                        } else {
                            args += bindValue(raw)
                        }
                    }
                    if (orphan) { result.skippedOrphans++; continue }
                    val cols = t.colNames
                    val newKey: String = when (t.key) {
                        Key.AUTO -> if (localRow != null) {
                            db.exec("UPDATE ${t.name} SET ${cols.joinToString(", ") { "$it = ?" }} WHERE id = ?", *(args + localKey!!.toLong()).toTypedArray())
                            localKey
                        } else {
                            db.exec("INSERT INTO ${t.name} (${cols.joinToString(", ")}) VALUES (${cols.joinToString(", ") { "?" }})", *args.toTypedArray())
                            db.lastInsertId().toString()
                        }
                        Key.DIARY, Key.CHECK -> {
                            val m = cols.zip(args).toMap()
                            val k = if (t.key == Key.DIARY) "${m["epochDay"]}" else "${m["routineId"]}:${m["epochDay"]}"
                            if (localRow != null && localKey != k) deleteRow(db, t, localKey)
                            db.exec("INSERT OR REPLACE INTO ${t.name} (${cols.joinToString(", ")}) VALUES (${cols.joinToString(", ") { "?" }})", *args.toTypedArray())
                            k
                        }
                    }
                    // 같은 자리를 쓰던 다른 uid(따로 만든 같은 날 일기 등)는 삭제로 남긴다
                    db.exec(
                        "UPDATE sync_meta SET rowKey = NULL, deleted = 1, updatedAt = ?, seq = ? WHERE tbl = ? AND rowKey = ? AND uid <> ?",
                        c.at, SyncSchema.nextSeq(db), t.name, newKey, c.uid,
                    )
                    upsertMeta(db, t.name, c.uid, newKey, c.at, false, seq())
                    if (t.name == "restaurant_visits") touchedRestaurants += args[0] as Long
                }
                result.applied += c.id
                result.changedTables += t.name
            }
            // 방문 기록이 바뀐 맛집은 방문 횟수·최근 방문일을 기록에 맞춘다 (화면에서 하던 것과 같음)
            for (rid in touchedRestaurants) {
                db.exec(
                    "UPDATE restaurants SET visitCount = (SELECT COUNT(*) FROM restaurant_visits WHERE restaurantId = ?), " +
                        "lastVisitEpochDay = (SELECT MAX(epochDay) FROM restaurant_visits WHERE restaurantId = ?), " +
                        "wish = CASE WHEN EXISTS (SELECT 1 FROM restaurant_visits WHERE restaurantId = ?) THEN 0 ELSE wish END WHERE id = ?",
                    rid, rid, rid, rid,
                )
            }
        } finally {
            SyncSchema.setMute(db, false)
        }
        result
    }

    private fun rowByKey(db: SyncDb, t: Table, key: String): Map<String, Any?>? {
        val cols = (if (t.key == Key.AUTO) listOf("id") else emptyList()) + t.colNames
        val sel = "SELECT ${cols.joinToString(", ")} FROM ${t.name}"
        return when (t.key) {
            Key.AUTO -> db.query("$sel WHERE id = ?", key.toLong())
            Key.DIARY -> db.query("$sel WHERE epochDay = ?", key.toLong())
            Key.CHECK -> key.split(':').let { db.query("$sel WHERE routineId = ? AND epochDay = ?", it[0].toLong(), it[1].toLong()) }
        }.firstOrNull()
    }

    private fun deleteRow(db: SyncDb, t: Table, key: String?) {
        key ?: return
        when (t.key) {
            Key.AUTO -> db.exec("DELETE FROM ${t.name} WHERE id = ?", key.toLong())
            Key.DIARY -> db.exec("DELETE FROM ${t.name} WHERE epochDay = ?", key.toLong())
            Key.CHECK -> key.split(':').let { db.exec("DELETE FROM ${t.name} WHERE routineId = ? AND epochDay = ?", it[0].toLong(), it[1].toLong()) }
        }
    }

    private fun upsertMeta(db: SyncDb, table: String, uid: String, rowKey: String?, at: Long, deleted: Boolean, seq: Long) {
        db.exec(
            "INSERT OR REPLACE INTO sync_meta (tbl, uid, rowKey, updatedAt, deleted, seq) VALUES (?, ?, ?, ?, ?, ?)",
            table, uid, rowKey, at, if (deleted) 1L else 0L, seq,
        )
    }

    fun toJson(list: List<Change>): JSONArray = JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }
    fun fromJson(a: JSONArray?): List<Change> = if (a == null) emptyList() else List(a.length()) { Change.fromJson(a.getJSONObject(it)) }

    // ───────────── PC(서버) 쪽 ─────────────

    /**
     * 휴대폰이 보낸 {since, changes}를 받아 합치고, 휴대폰이 아직 모르는 변경을 돌려준다.
     * 돌려주는 것: {seq, changes}
     */
    fun serve(db: SyncDb, request: JSONObject): JSONObject = db.tx {
        val since = request.optLong("since", 0L)
        val incoming = fromJson(request.optJSONArray("changes"))
        val result = apply(db, incoming, assignSeq = true)
        val out = LinkedHashMap<String, Change>()
        collect(db, since).filter { it.id !in result.applied }.forEach { out[it.id] = it }
        // 휴대폰 것이 더 오래되어 버렸으면 이쪽 것을 돌려줘서 양쪽이 같아지게 한다
        collectIds(db, result.rejected).forEach { out[it.id] = it }
        JSONObject().put("seq", SyncSchema.currentSeq(db)).put("changes", toJson(out.values.toList()))
            .put("received", incoming.size).put("changed", JSONArray(result.changedTables.toList()))
    }

    // ───────────── 휴대폰(클라이언트) 쪽 ─────────────

    /** 휴대폰이 기억하는 동기화 위치 */
    data class ClientState(val serverSeq: Long, val sentSeq: Long) {
        companion object {
            /** 처음 짝을 맺었을 때: 모두 주고받는다 */
            val FRESH = ClientState(0L, -1L)
        }
    }

    class ClientResult(val state: ClientState, val sent: Int, val received: Int, val changedTables: Set<String>)

    /** 한 번 주고받기. [post]는 요청 JSON을 PC에 보내고 응답 JSON을 돌려준다 */
    fun clientSync(db: SyncDb, state: ClientState, post: (JSONObject) -> JSONObject): ClientResult {
        val (mark, outgoing) = db.tx { SyncSchema.currentSeq(db) to collect(db, state.sentSeq) }
        val req = JSONObject().put("since", state.serverSeq).put("changes", toJson(outgoing))
        val resp = post(req)
        val serverSeq = resp.getLong("seq")
        if (serverSeq < state.serverSeq) {
            // PC 쪽 기록이 새로 시작됐다 (다시 설치 등): 처음부터 다시 맞춘다
            if (state != ClientState.FRESH) return clientSync(db, ClientState.FRESH, post)
        }
        val incoming = fromJson(resp.optJSONArray("changes"))
        val result = apply(db, incoming, assignSeq = false)
        val changed = result.changedTables + resp.optJSONArray("changed").let { a ->
            if (a == null) emptySet() else List(a.length()) { a.getString(it) }.toSet()
        }
        return ClientResult(ClientState(serverSeq, mark), outgoing.size, result.applied.size, changed)
    }
}
