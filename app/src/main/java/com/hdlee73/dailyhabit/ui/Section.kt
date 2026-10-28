package com.hdlee73.dailyhabit.ui

import com.hdlee73.dailyhabit.BuildConfig

/** 메뉴 항목. 순서(ordinal)는 알림 인텐트에서도 쓰이므로 끝에만 추가할 것 */
enum class Section(val label: String) {
    GOSPEL("오늘의 말씀"),
    PRAYER("기도"),
    SCHEDULE("일정"),
    TODO("할 일"),
    ROUTINE("루틴"),
    SETTINGS("설정"),
    QUOTE("오늘의 명언"),
    RESTAURANT("맛집"),
    JOURNAL("한 줄 일기"),
    SHOPPING("장보기"), // 메뉴에서는 숨김 (저장된 기록은 보존)
    BOOKS("독서"),
    ABOUT("앱 정보"),
    REVIEW("월간 돌아보기"),
    HYMN("가톨릭 성가"),
    ;

    companion object {
        /** 판본별 첫 화면 */
        val HOME: Section get() = if (BuildConfig.CATHOLIC) GOSPEL else QUOTE

        /** 메뉴 위쪽 묶음 */
        val primary: List<Section> get() = if (BuildConfig.CATHOLIC) listOf(GOSPEL, PRAYER, HYMN) else listOf(QUOTE)

        /** '하루 관리' 묶음 */
        val daily = listOf(SCHEDULE, TODO, ROUTINE, JOURNAL, RESTAURANT, BOOKS, REVIEW)

        fun available(): List<Section> = primary + daily + SETTINGS + ABOUT

        fun fromIndex(i: Int): Section {
            val s = entries.getOrElse(i) { HOME }
            // 다른 판본 전용 화면은 첫 화면으로
            return if (s in available()) s else HOME
        }
    }
}
