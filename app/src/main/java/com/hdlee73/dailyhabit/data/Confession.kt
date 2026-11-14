package com.hdlee73.dailyhabit.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 양심성찰 체크리스트 한 항목 */
data class ConscienceItem(val id: String, val text: String)

/** 양심성찰 묶음 (십계명 기준) */
data class ConscienceGroup(
    val title: String,
    val subtitle: String,
    val items: List<ConscienceItem>,
)

/**
 * 고해성사 준비(양심성찰 체크리스트 · 성사 순서)와 마지막 성사일 안내.
 */
object ConfessionGuide {
    val groups: List<ConscienceGroup> = listOf(
        ConscienceGroup(
            "하느님께 대한 의무",
            "제1~3계명",
            listOf(
                ConscienceItem("g1_1", "주일 미사와 의무 축일 미사에 합당한 이유 없이 빠진 적은 없는가?"),
                ConscienceItem("g1_2", "미사 중에 성의를 다하지 못하거나 성체를 모시기 전 준비를 소홀히 하지 않았는가?"),
                ConscienceItem("g1_3", "매일 기도와 말씀 읽기를 게을리하며 하느님을 멀리하지 않았는가?"),
                ConscienceItem("g1_4", "하느님의 이름을 함부로 부르거나 미신·점술에 마음을 둔 적은 없는가?"),
                ConscienceItem("g1_5", "어려움 속에서 하느님의 사랑을 의심하거나 원망하지 않았는가?"),
            ),
        ),
        ConscienceGroup(
            "가족과 공동체",
            "제4계명",
            listOf(
                ConscienceItem("g2_1", "부모님과 어른을 공경하지 못하거나 마음을 아프게 한 일은 없는가?"),
                ConscienceItem("g2_2", "배우자·자녀·가족에게 사랑과 인내 대신 짜증이나 무관심으로 대하지 않았는가?"),
                ConscienceItem("g2_3", "가정과 일터·공동체에서 맡은 책임을 성실히 다했는가?"),
            ),
        ),
        ConscienceGroup(
            "생명 · 마음과 말",
            "제5계명",
            listOf(
                ConscienceItem("g3_1", "남을 미워하거나 원한을 품고 용서하지 않은 일은 없는가?"),
                ConscienceItem("g3_2", "화를 참지 못해 거친 말이나 행동으로 이웃에게 상처를 주지 않았는가?"),
                ConscienceItem("g3_3", "내 몸과 마음의 건강을 해치는 무절제한 생활을 하지 않았는가?"),
            ),
        ),
        ConscienceGroup(
            "순결과 절제",
            "제6·9계명",
            listOf(
                ConscienceItem("g4_1", "생각과 말과 행동에서 순결을 지키지 못한 일은 없는가?"),
                ConscienceItem("g4_2", "음란물이나 부정한 욕망에 마음을 빼앗기지 않았는가?"),
            ),
        ),
        ConscienceGroup(
            "정직과 나눔",
            "제7·10계명",
            listOf(
                ConscienceItem("g5_1", "정직하지 못한 방법으로 이익을 취하거나 남의 것을 탐내지 않았는가?"),
                ConscienceItem("g5_2", "남의 물건이나 권리에 손해를 끼치고 갚지 않은 일은 없는가?"),
                ConscienceItem("g5_3", "시간·재물·재능을 이웃과 어려운 이들을 위해 나누는 데 인색하지 않았는가?"),
            ),
        ),
        ConscienceGroup(
            "진실과 겸손",
            "제8계명",
            listOf(
                ConscienceItem("g6_1", "거짓말을 하거나 겉과 다른 말로 남을 속이지 않았는가?"),
                ConscienceItem("g6_2", "남을 험담하고 비방하거나 섣불리 판단하여 명예를 훼손하지 않았는가?"),
                ConscienceItem("g6_3", "교만하고 이기적인 마음으로 남을 무시하거나 내 잘못을 감추지 않았는가?"),
            ),
        ),
    )

    val allItemIds: Set<String> by lazy { groups.flatMap { it.items }.map { it.id }.toSet() }

    val steps: List<Pair<String, String>> = listOf(
        "성찰" to "성령의 도우심을 청하며 지난 시간의 생각·말·행동을 돌아봅니다.",
        "통회" to "지은 죄를 진심으로 뉘우치고, 다시 짓지 않기로 굳게 다짐합니다.",
        "고백" to "고해실에 들어가 성호를 긋고, 마지막 고해성사 시기와 잘못을 솔직하게 고백합니다.",
        "보속과 통회기도" to "신부님이 주신 보속을 마음에 새기고, 통회기도를 바친 뒤 사죄경을 듣습니다.",
        "감사" to "성사가 끝나면 주님께 감사드리고, 정해진 보속을 잊지 않고 실천합니다.",
    )

    fun daysSince(lastDate: LocalDate, today: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(lastDate, today).coerceAtLeast(0L)

    fun formatElapsed(days: Long): String = when {
        days <= 0L -> "오늘"
        days == 1L -> "어제"
        days < 30L -> "${days}일 지남"
        days < 365L -> "${days}일 지남 · 약 ${days / 30}개월"
        else -> "${days}일 지남 · 약 ${days / 365}년"
    }

    /** 고해소에서 고백을 시작할 때 참고하는 첫 인사 */
    fun confessionOpening(lastDate: LocalDate?, today: LocalDate = LocalDate.now()): String {
        if (lastDate == null) return "“신부님, 마지막 고해성사 본 지 ○○일(개월) 되었습니다.”"
        val days = daysSince(lastDate, today)
        val period = when {
            days == 0L -> "오늘"
            days < 7L -> "${days}일"
            days < 30L -> "약 ${days / 7}주(${days}일)"
            days < 365L -> "약 ${days / 30}개월"
            else -> "약 ${days / 365}년"
        }
        return "“신부님, 마지막 고해성사 본 지 ${period} 되었습니다.”"
    }
}
