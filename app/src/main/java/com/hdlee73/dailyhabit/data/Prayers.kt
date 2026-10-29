package com.hdlee73.dailyhabit.data

import java.time.DayOfWeek
import java.time.LocalDate

data class PrayerItem(
    val id: String,
    val title: String,
    val category: String,
    /** 본문. ◎ 는 이끄는 이, ● 는 함께 응답하는 부분 */
    val text: String,
    val note: String = "",
)

/** 천주교 주요 기도문 (한국천주교주교회의 「가톨릭 기도서」 기준) */
object Prayers {
    const val ROSARY_ID = "rosary"

    val categories = listOf("기본 기도", "하루 기도", "성월 기도", "성모 기도", "평화와 교회", "가정 기도", "참회와 위령")

    private val peace = PrayerItem(
        "peace", "평화를 구하는 기도", "평화와 교회",
        """
        주님, 저를 평화의 도구로 써 주소서.
        미움이 있는 곳에 사랑을,
        다툼이 있는 곳에 용서를,
        분열이 있는 곳에 일치를,
        의혹이 있는 곳에 신앙을,
        그릇됨이 있는 곳에 진리를,
        절망이 있는 곳에 희망을,
        어둠에 빛을,
        슬픔이 있는 곳에 기쁨을 가져오는 자 되게 하소서.
        위로받기보다는 위로하고,
        이해받기보다는 이해하며,
        사랑받기보다는 사랑하게 하여 주소서.
        우리는 줌으로써 받고,
        용서함으로써 용서받으며,
        자기를 버리고 죽음으로써
        영원한 생명을 얻기 때문입니다.
        """,
        note = "아시시의 성 프란치스코의 평화의 기도로 널리 알려진 기도입니다.",
    )

    private val rosary = PrayerItem(ROSARY_ID, "묵주기도", "성모 기도", "")

    val all: List<PrayerItem> by lazy {
        val official = OFFICIAL_PRAYERS
        val marian = official.filter { it.category == "성모 기도" }
        // 성모 기도는 묵주기도를 맨 앞에, 평화를 구하는 기도는 '평화와 교회' 맨 앞에
        val ordered = official.filter { it.category != "성모 기도" }.toMutableList()
        val firstChurch = ordered.indexOfFirst { it.category == "평화와 교회" }.let { if (it < 0) ordered.size else it }
        ordered.add(firstChurch, peace)
        ordered + rosary + marian + MonthlyPrayers.all.map { it.prayer }
    }

    fun byId(id: String): PrayerItem? = all.firstOrNull { it.id == id }

    /** 아침 알림에 날마다 돌아가며 넣는 가정 기도 */
    val familyRotation: List<PrayerItem> by lazy { all.filter { it.category == "가정 기도" } }

    fun familyFor(date: LocalDate): PrayerItem = familyRotation[(date.toEpochDay().mod(familyRotation.size.toLong())).toInt()]

    // ── 묵주기도
    data class Mystery(val name: String, val days: String, val decades: List<String>)

    val mysteries = listOf(
        Mystery(
            "환희의 신비", "월요일 · 토요일",
            listOf(
                "마리아께서 예수님을 잉태하심을 묵상합시다.",
                "마리아께서 엘리사벳을 찾아보심을 묵상합시다.",
                "마리아께서 예수님을 낳으심을 묵상합시다.",
                "마리아께서 예수님을 성전에 바치심을 묵상합시다.",
                "마리아께서 잃으셨던 예수님을 성전에서 찾으심을 묵상합시다.",
            ),
        ),
        Mystery(
            "빛의 신비", "목요일",
            listOf(
                "예수님께서 세례받으심을 묵상합시다.",
                "예수님께서 카나에서 첫 기적을 행하심을 묵상합시다.",
                "예수님께서 하느님 나라를 선포하심을 묵상합시다.",
                "예수님께서 거룩하게 변모하심을 묵상합시다.",
                "예수님께서 성체성사를 세우심을 묵상합시다.",
            ),
        ),
        Mystery(
            "고통의 신비", "화요일 · 금요일",
            listOf(
                "예수님께서 우리를 위하여 피땀 흘리심을 묵상합시다.",
                "예수님께서 우리를 위하여 매맞으심을 묵상합시다.",
                "예수님께서 우리를 위하여 가시관 쓰심을 묵상합시다.",
                "예수님께서 우리를 위하여 십자가 지심을 묵상합시다.",
                "예수님께서 우리를 위하여 십자가에 못 박혀 돌아가심을 묵상합시다.",
            ),
        ),
        Mystery(
            "영광의 신비", "수요일 · 주일",
            listOf(
                "예수님께서 부활하심을 묵상합시다.",
                "예수님께서 승천하심을 묵상합시다.",
                "예수님께서 성령을 보내심을 묵상합시다.",
                "예수님께서 마리아를 하늘에 불러올리심을 묵상합시다.",
                "예수님께서 마리아께 천상 모후의 관을 씌우심을 묵상합시다.",
            ),
        ),
    )

    fun mysteryFor(date: LocalDate): Mystery = when (date.dayOfWeek) {
        DayOfWeek.MONDAY, DayOfWeek.SATURDAY -> mysteries[0]
        DayOfWeek.THURSDAY -> mysteries[1]
        DayOfWeek.TUESDAY, DayOfWeek.FRIDAY -> mysteries[2]
        else -> mysteries[3]
    }

    val rosaryOrder = listOf(
        "묵주의 십자가를 잡고 십자성호를 그은 다음 사도신경을 바칩니다.",
        "큰 알에서 주님의 기도 1번, 작은 알 3개에서 성모송 각 1번, 이어서 영광송을 바칩니다. 구원을 비는 기도를 바칠 수 있습니다.",
        "신비 제1단을 묵상한 뒤 주님의 기도 1번, 작은 알 10개에서 성모송 10번, 큰 알에서 영광송을 바칩니다. 구원을 비는 기도를 바칠 수 있습니다.",
        "같은 방법으로 제2단부터 제5단까지 바칩니다.",
        "성모 찬송을 바칠 수 있으며, 십자가를 잡고 성호경으로 마칩니다.",
    )
}
