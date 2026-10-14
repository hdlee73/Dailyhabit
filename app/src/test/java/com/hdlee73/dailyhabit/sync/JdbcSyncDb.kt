package com.hdlee73.dailyhabit.sync

import java.sql.Connection

/** JDBC(sqlite-jdbc)로 쓰는 SyncDb. 윈도우 앱과 두 앱의 테스트에서 쓴다 */
class JdbcSyncDb(private val conn: Connection) : SyncDb {
    private var depth = 0

    private fun bind(ps: java.sql.PreparedStatement, args: Array<out Any?>) {
        args.forEachIndexed { i, a ->
            when (a) {
                null -> ps.setObject(i + 1, null)
                is Boolean -> ps.setLong(i + 1, if (a) 1 else 0)
                is Int -> ps.setLong(i + 1, a.toLong())
                is Long -> ps.setLong(i + 1, a)
                is Double -> ps.setDouble(i + 1, a)
                is Float -> ps.setDouble(i + 1, a.toDouble())
                is ByteArray -> ps.setBytes(i + 1, a)
                else -> ps.setString(i + 1, a.toString())
            }
        }
    }

    override fun query(sql: String, vararg args: Any?): List<Map<String, Any?>> =
        conn.prepareStatement(sql).use { ps ->
            bind(ps, args)
            ps.executeQuery().use { rs ->
                val md = rs.metaData
                val out = ArrayList<Map<String, Any?>>()
                while (rs.next()) {
                    val row = HashMap<String, Any?>()
                    for (i in 1..md.columnCount) {
                        row[md.getColumnLabel(i)] = when (val v = rs.getObject(i)) {
                            is Int -> v.toLong()
                            is Short -> v.toLong()
                            is Float -> v.toDouble()
                            else -> v
                        }
                    }
                    out.add(row)
                }
                out
            }
        }

    override fun exec(sql: String, vararg args: Any?) {
        conn.prepareStatement(sql).use { ps ->
            bind(ps, args)
            ps.execute()
        }
    }

    override fun <T> tx(block: () -> T): T {
        if (depth > 0) {
            depth++
            try { return block() } finally { depth-- }
        }
        val auto = conn.autoCommit
        conn.autoCommit = false
        depth = 1
        try {
            val r = block()
            conn.commit()
            return r
        } catch (e: Throwable) {
            runCatching { conn.rollback() }
            throw e
        } finally {
            depth = 0
            conn.autoCommit = auto
        }
    }
}
