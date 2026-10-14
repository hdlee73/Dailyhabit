package com.hdlee73.dailyhabit.data

import android.database.Cursor
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hdlee73.dailyhabit.sync.SyncDb

/**
 * 동기화 엔진이 쓰는 SQLite 창구 (안드로이드).
 * [room]을 주면 트랜잭션을 Room으로 열어, 끝난 뒤 화면(Flow)이 바뀐 기록을 다시 읽는다.
 */
class AndroidSyncDb(private val db: SupportSQLiteDatabase, private val room: RoomDatabase? = null) : SyncDb {

    override fun query(sql: String, vararg args: Any?): List<Map<String, Any?>> =
        db.query(SimpleSQLiteQuery(sql, args.map(::bindable).toTypedArray())).use { c ->
            val out = ArrayList<Map<String, Any?>>()
            while (c.moveToNext()) {
                val row = HashMap<String, Any?>()
                for (i in 0 until c.columnCount) {
                    row[c.getColumnName(i)] = when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
                        else -> c.getString(i)
                    }
                }
                out.add(row)
            }
            out
        }

    override fun exec(sql: String, vararg args: Any?) {
        if (args.isEmpty()) db.execSQL(sql) else db.execSQL(sql, args.map(::bindable).toTypedArray())
    }

    override fun <T> tx(block: () -> T): T {
        if (room != null) return room.runInTransaction(java.util.concurrent.Callable { block() })
        db.beginTransaction()
        try {
            val r = block()
            db.setTransactionSuccessful()
            return r
        } finally {
            db.endTransaction()
        }
    }

    private fun bindable(a: Any?): Any? = when (a) {
        is Boolean -> if (a) 1L else 0L
        is Int -> a.toLong()
        is Float -> a.toDouble()
        else -> a
    }

    companion object {
        fun of(room: RoomDatabase) = AndroidSyncDb(room.openHelper.writableDatabase, room)
    }
}
