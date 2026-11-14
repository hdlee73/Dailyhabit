package com.hdlee73.dailyhabit.data

import android.content.Context

/** 사용자 설정 */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var dailyEnabled: Boolean
        get() = prefs.getBoolean(KEY_DAILY_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_DAILY_ENABLED, v).apply()

    /** 아침 알림 시각 (자정부터 분) */
    var dailyMinute: Int
        get() = prefs.getInt(KEY_DAILY_MINUTE, 9 * 60)
        set(v) = prefs.edit().putInt(KEY_DAILY_MINUTE, v).apply()

    /** 천주교인용 기본 루틴(말씀 읽기·기도하기)을 이미 만들었는지 */
    var faithRoutinesSeeded: Boolean
        get() = prefs.getBoolean(KEY_FAITH_SEEDED, false)
        set(v) = prefs.edit().putBoolean(KEY_FAITH_SEEDED, v).apply()

    /** 루틴을 제목과 체크만 보이는 작은 카드로 보기 */
    var routineCompact: Boolean
        get() = prefs.getBoolean(KEY_ROUTINE_COMPACT, true)
        set(v) = prefs.edit().putBoolean(KEY_ROUTINE_COMPACT, v).apply()

    /** 그 해에 읽고 싶은 책 권수 (0이면 목표 없음) */
    fun readingGoal(year: Int): Int = prefs.getInt("reading_goal_$year", 0)

    fun setReadingGoal(year: Int, count: Int) = prefs.edit().putInt("reading_goal_$year", count).apply()

    /** 앱을 열 때 새 버전이 있는지 확인 */
    var autoUpdateCheck: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE, true)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_UPDATE, v).apply()

    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, v).apply()

    var lastBackup: Long
        get() = prefs.getLong(KEY_LAST_BACKUP, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_BACKUP, v).apply()

    /** 장보기에 최근 넣은 품목 이름 (최근 것이 앞) */
    var shoppingHistory: List<String>
        get() = prefs.getString(KEY_SHOPPING_HISTORY, "").orEmpty().split('\n').filter { it.isNotBlank() }
        set(v) = prefs.edit().putString(KEY_SHOPPING_HISTORY, v.joinToString("\n")).apply()

    /** 마지막 고해성사 날짜 (epochDay, 미기록이면 null) */
    var lastConfessionEpochDay: Long?
        get() = if (prefs.contains(KEY_LAST_CONFESSION)) prefs.getLong(KEY_LAST_CONFESSION, 0L) else null
        set(v) {
            val e = prefs.edit()
            if (v == null) e.remove(KEY_LAST_CONFESSION) else e.putLong(KEY_LAST_CONFESSION, v)
            e.apply()
        }

    /** 양심성찰 체크리스트에서 선택한 항목 ID (로컬에만 보관) */
    var confessionCheckedIds: Set<String>
        get() = prefs.getString(KEY_CONFESSION_CHECKED, "").orEmpty().split('\n').filter { it.isNotBlank() }.toSet()
        set(v) = prefs.edit().putString(KEY_CONFESSION_CHECKED, v.joinToString("\n")).apply()

    /** 고해성사 준비 메모 (로컬에만 보관) */
    var confessionMemo: String
        get() = prefs.getString(KEY_CONFESSION_MEMO, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_CONFESSION_MEMO, v).apply()

    companion object {
        private const val KEY_FAITH_SEEDED = "faith_routines_seeded"
        private const val KEY_ROUTINE_COMPACT = "routine_compact"
        private const val KEY_AUTO_UPDATE = "auto_update_check"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_LAST_BACKUP = "last_backup"
        private const val KEY_SHOPPING_HISTORY = "shopping_history"
        private const val KEY_DAILY_ENABLED = "daily_enabled"
        private const val KEY_DAILY_MINUTE = "daily_minute"
        private const val KEY_LAST_CONFESSION = "last_confession_epoch_day"
        private const val KEY_CONFESSION_CHECKED = "confession_checked_ids"
        private const val KEY_CONFESSION_MEMO = "confession_memo"
    }
}
