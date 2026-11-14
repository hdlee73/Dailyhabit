package com.hdlee73.dailyhabit.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 양심성찰 체크리스트 한 항목 */
data class ConscienceItem(val id: String, val text: String)

/** 양심성찰 묶음 (십계명 및 교회 계명·칠죄종·일상 성찰) */
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
            "하느님을 첫째로 섬김",
            "제1계명",
            listOf(
                ConscienceItem("g1_3", "매일 바치는 기도나 말씀 읽기를 게을리하며 하느님과 멀어진 적은 없는가?"),
                ConscienceItem("g1_5", "시련이나 어려움 속에서 하느님의 사랑과 섭리를 의심하거나 원망하지 않았는가?"),
                ConscienceItem("g1_4", "사주·타로·점·부적·징크스 등 미신이나 주술적인 것에 마음을 기대지 않았는가?"),
                ConscienceItem("g1_6", "돈·성공·체면·쾌락·스마트폰 등을 하느님보다 더 중요한 우상처럼 섬기지는 않았는가?"),
                ConscienceItem("g1_7", "세속적 풍조나 시선 때문에 내가 천주교 신자임을 숨기거나 신앙을 부끄러워한 적은 없는가?"),
            ),
        ),
        ConscienceGroup(
            "주님의 이름과 거룩한 것",
            "제2계명",
            listOf(
                ConscienceItem("c2_1", "하느님·성모님·성인의 이름을 분노나 농담 중에 함부로 부르거나 욕설에 섞어 쓰지 않았는가?"),
                ConscienceItem("c2_2", "거짓 맹세를 하거나, 지키지 못할 약속에 하느님의 이름을 경솔히 걸지 않았는가?"),
                ConscienceItem("c2_3", "성당·성사·기도문·성물 등 거룩한 것들을 가볍고 불경한 태도로 대하지 않았는가?"),
            ),
        ),
        ConscienceGroup(
            "주일과 축일을 거룩히 지냄",
            "제3계명",
            listOf(
                ConscienceItem("g1_1", "주일 미사와 의무 축일(성탄·성모 승천·천주의 성모 마리아 대축일) 미사에 합당한 이유 없이 빠진 적은 없는가?"),
                ConscienceItem("g1_2", "미사에 지각하거나, 미사 중에 집중하지 못하고 성체를 모시기 전 공심재와 마음 준비를 소홀히 하지 않았는가?"),
                ConscienceItem("c3_3", "주일을 주님께 감사하고 쉬며 가족·이웃과 사랑을 나누는 날로 보내지 못하고, 무절제한 유흥이나 일에만 매달리지 않았는가?"),
                ConscienceItem("c3_4", "피치 못할 사정으로 주일 미사에 참례하지 못했을 때 말씀 묵상과 기도로 주일을 기리는 노력조차 하지 않았는가?"),
            ),
        ),
        ConscienceGroup(
            "가정과 웃어른 공경",
            "제4계명",
            listOf(
                ConscienceItem("g2_1", "부모님과 어른을 공경하지 못하거나, 퉁명스러운 말과 무관심으로 마음을 아프게 한 일은 없는가?"),
                ConscienceItem("c4_2", "연로하시거나 편찮으신 부모님·가족을 자주 찾아뵙거나 안부를 살피고 돌보는 일을 소홀히 하지 않았는가?"),
                ConscienceItem("g2_2", "배우자·자녀·가족에게 사랑과 인내 대신 짜증, 무시, 폭언이나 지나친 통제로 상처를 주지 않았는가?"),
                ConscienceItem("c4_4", "자녀나 아랫사람에게 올바른 본보기가 되어 주지 못하거나 신앙과 인격 성장을 돌보지 않은 일은 없는가?"),
                ConscienceItem("g2_3", "가정·직장·공동체에서 내가 맡은 역할과 합당한 질서를 불성실하게 대하거나 회피하지 않았는가?"),
            ),
        ),
        ConscienceGroup(
            "생명과 마음의 평화",
            "제5계명",
            listOf(
                ConscienceItem("g3_1", "남을 미워하거나 원한을 품고, 잘못을 뉘우치는 이나 가족·이웃을 끝내 용서하지 않은 일은 없는가?"),
                ConscienceItem("g3_2", "화를 참지 못해 거친 말·고함·냉담함·폭력적인 행동으로 이웃에게 깊은 상처를 주지 않았는가?"),
                ConscienceItem("c5_3", "남이 잘 안 되기를 바라거나 복수하고 싶은 마음, 분노를 마음속에 오래 키워 오지 않았는가?"),
                ConscienceItem("g3_3", "과음·흡연·폭식·수면 부족 등 내 몸과 마음의 건강을 해치는 무절제한 생활을 하지 않았는가?"),
                ConscienceItem("c5_5", "난폭·음주 운전이나 안전 수칙 무시로 나와 타인의 생명을 위험에 빠뜨린 적은 없는가?"),
            ),
        ),
        ConscienceGroup(
            "순결과 가정의 성실",
            "제6·9계명",
            listOf(
                ConscienceItem("g4_1", "생각과 시선, 말과 행동에서 순결과 정숙함을 지키지 못한 일은 없는가?"),
                ConscienceItem("g4_2", "음란물, 선정적인 영상·글, 부정한 상상에 마음을 빼앗기거나 머물러 있지 않았는가?"),
                ConscienceItem("c6_3", "성적인 농담이나 타인을 성적 대상으로 바라보는 언행으로 인간의 존엄을 깎아내리지 않았는가?"),
                ConscienceItem("c6_4", "부부의 신의와 사랑에 어긋나는 생각이나 행동을 하거나, 가정 밖에서 부적절한 친밀함을 추구하지 않았는가?"),
            ),
        ),
        ConscienceGroup(
            "정직 · 나눔 · 탐욕 절제",
            "제7·10계명",
            listOf(
                ConscienceItem("g5_1", "부당한 방법으로 이익을 취하거나 세금·요금·공금을 속이고 남의 것을 탐내지 않았는가?"),
                ConscienceItem("g5_2", "빌린 돈이나 물건, 남에게 끼친 손해를 제때 갚지 않거나 정당한 대가(임금·값)를 후려치지 않았는가?"),
                ConscienceItem("c7_3", "일터나 학교에서 보수에 맞는 성실함을 다하지 않고 근무·학업 시간을 게으르게 보내지 않았는가?"),
                ConscienceItem("c7_4", "불법 복제·무단 도용 등 남의 지식재산과 수고를 대가 없이 가로채지 않았는가?"),
                ConscienceItem("g5_3", "시간·재물·재능을 가난하고 어려운 이웃과 나누는 데 인색하거나 외면하지 않았는가?"),
                ConscienceItem("c7_6", "가진 것에 감사하지 못하고 탐욕·사치·낭비에 빠지거나 자연환경을 함부로 훼손하지 않았는가?"),
            ),
        ),
        ConscienceGroup(
            "진실함과 이웃의 명예",
            "제8계명",
            listOf(
                ConscienceItem("g6_1", "불리한 상황을 모면하려고 거짓말·과장·핑계를 대거나, 겉과 속이 다른 위선을 부리지 않았는가?"),
                ConscienceItem("g6_2", "없는 자리에서 남을 험담하고 비방하거나, 확인되지 않은 소문을 퍼뜨려 명예를 훼손하지 않았는가?"),
                ConscienceItem("c8_3", "겉모습이나 소문만으로 남을 섣불리 판단·단죄하고 악의적으로 해석하지 않았는가?"),
                ConscienceItem("c8_4", "남이 믿고 맡긴 비밀을 가볍게 발설하거나, 약속을 지키지 않아 신뢰를 저버리지 않았는가?"),
            ),
        ),
        ConscienceGroup(
            "교회의 계명 · 성사 생활",
            "십계명 외",
            listOf(
                ConscienceItem("ch_1", "적어도 1년에 한 번 이상 고해성사를 보고 부활 시기에 성체를 모셔야 하는 교회 의무를 소홀히 하지 않았는가?"),
                ConscienceItem("ch_2", "대죄를 지은 상태이거나 충분한 반성 없이 습관적·형식적으로 성체를 모신 적은 없는가?"),
                ConscienceItem("ch_3", "지난 고해성사에서 죄를 일부러 숨기거나, 신부님이 주신 보속 기도와 실천을 잊고 미루지 않았는가?"),
                ConscienceItem("ch_4", "재의 수요일·성금요일의 단식과 금육, 매주 금요일의 금육(또는 대속 선행)을 소홀히 하지 않았는가?"),
                ConscienceItem("ch_5", "본당 공동체와 교회의 유지·선교를 위한 교무금·헌금·봉사에 형편만큼 동참하는 데 인색하지 않았는가?"),
            ),
        ),
        ConscienceGroup(
            "마음의 뿌리 (칠죄종 성찰)",
            "십계명 외",
            listOf(
                ConscienceItem("g6_3", "내가 항상 옳다는 교만과 고집으로 남의 조언을 무시하고, 인정받으려는 허영심에 사로잡히지 않았는가?"),
                ConscienceItem("sin_2", "남의 성공이나 행복을 진심으로 축하하지 못하고 시기·질투하며 속으로 깎아내리지 않았는가?"),
                ConscienceItem("sin_3", "내 것만 챙기려는 인색함으로 주변 사람의 필요를 모른 척하고 계산적으로 대하지 않았는가?"),
                ConscienceItem("sin_4", "뜻대로 되지 않는다고 쉽게 분노하거나 짜증을 내며 주변 분위기를 무겁게 만들지 않았는가?"),
                ConscienceItem("sin_5", "기도와 선행, 해야 할 일을 알면서도 귀찮아서 미루는 영적 나태와 게으름에 빠지지 않았는가?"),
                ConscienceItem("sin_6", "음식·쇼핑·영상·게임·SNS 등 감각적 즐거움을 절제 없이 쫓는 무절제로 시간을 허비하지 않았는가?"),
            ),
        ),
        ConscienceGroup(
            "일상 · 공동체 · 디지털 생활",
            "십계명 외",
            listOf(
                ConscienceItem("soc_1", "인터넷 댓글·커뮤니티·메신저에서 익명 뒤에 숨어 남에게 상처 주는 말이나 혐오 표현을 쓰지 않았는가?"),
                ConscienceItem("soc_2", "확인되지 않은 정보나 자극적인 비방 글을 사실 확인 없이 퍼 나르거나 동조하지 않았는가?"),
                ConscienceItem("soc_3", "약자·외국인·소수자·나와 생각이나 처지가 다른 이들을 편견으로 배척하거나 무시하지 않았는가?"),
                ConscienceItem("soc_4", "내 잘못을 돌아보기 전에 남 탓·상황 탓을 먼저 하며, 사과와 화해를 미루지 않았는가?"),
                ConscienceItem("soc_5", "일상에서 받은 은총과 주변의 작은 호의에 감사할 줄 모르고 불평불만만 앞세우지 않았는가?"),
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
