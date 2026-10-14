package com.hdlee73.dailyhabit.sync

/**
 * 동기화 엔진이 쓰는 아주 작은 SQLite 창구. 안드로이드(Room)와 윈도우(JDBC)가 각자 구현한다.
 * 이 폴더(sync/)의 다른 파일은 두 앱에 똑같이 들어 있다.
 *
 * query 결과 값은 Long, Double, String, ByteArray, null 중 하나.
 */
interface SyncDb {
    fun query(sql: String, vararg args: Any?): List<Map<String, Any?>>
    fun exec(sql: String, vararg args: Any?)

    /** 블록 전체를 하나의 트랜잭션으로. 안에서 다시 불러도 된다 */
    fun <T> tx(block: () -> T): T

    fun lastInsertId(): Long = (query("SELECT last_insert_rowid() AS id").first()["id"] as Number).toLong()
}

internal fun Map<String, Any?>.long(col: String): Long = (this[col] as Number).toLong()
internal fun Map<String, Any?>.longOrNull(col: String): Long? = (this[col] as? Number)?.toLong()
internal fun Map<String, Any?>.str(col: String): String? = this[col] as? String
