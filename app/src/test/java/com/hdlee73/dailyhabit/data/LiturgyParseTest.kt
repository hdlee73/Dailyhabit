package com.hdlee73.dailyhabit.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 매일미사 페이지 구조(h3.bd_tit + 본문 상자)에서 독서·화답송·환호송·복음을 고르는지 */
class LiturgyParseTest {
    private fun section(title: String, vararg lines: String) =
        """<h3 class="bd_tit mt30 mb10">$title</h3>
           <div class="board_layout type6"><span>${lines.joinToString("<br>")}</span></div>"""

    private val page = """
        <html><body>
        <ul><li>(<span class="daycolor">백</span>)모든 성인 대축일</li></ul>
        <p class="m_t_today01">[(녹) 연중 제32주일]</p>
        ${section("입당송", "시편 88(87),3 참조", "주님, 제 기도가 당신 앞에 이르게 하소서.")}
        ${section("제1독서", "&lt;지혜를 찾는 이들은 지혜를 만난다.&gt;", "▥ 지혜서의 말씀입니다.6,12-16", "12 지혜는 바래지 않고 늘 빛이 나서", "주님의 말씀입니다.", "◎ 하느님, 감사합니다.")}
        ${section("화답송", "시편 63(62),2(◎ 2ㄷ 참조)", "◎ 주님, 제 영혼 당신을 목말라하나이다.", "○ 하느님, 당신은 저의 하느님. ◎")}
        ${section("제2독서", "▥ 사도 바오로의 테살로니카 1서 말씀입니다.4,13-18", "13 형제 여러분,", "주님의 말씀입니다.", "◎ 하느님, 감사합니다.", "&lt;또는&gt;", "▥ 사도 바오로의 테살로니카 1서 말씀입니다.4,13-14")}
        ${section("복음 환호송", "마태 24,42.44 참조", "◎ 알렐루야.", "○ 깨어 준비하고 있어라.", "◎ 알렐루야.")}
        ${section("복음", "&lt;신랑이 온다. 신랑을 맞으러 나가라.&gt;", "✠ 마태오가 전한 거룩한 복음입니다.25,1-13", "1 그때에 예수님께서 제자들에게 이런 비유를 들어 말씀하셨다.", "주님의 말씀입니다.", "◎ 그리스도님, 찬미합니다.")}
        ${section("예물기도", "주님, 이 예물을 받으소서.")}
        </body></html>
    """.trimIndent()

    @Test
    fun readingsInMassOrder() {
        val g = GospelRepository.parseHtml(page, LocalDate.of(2026, 11, 8), "u")
        assertEquals(listOf("제1독서", "화답송", "제2독서", "복음 환호송"), g.readings.map { it.kind })
        assertEquals("(녹) 연중 제32주일", g.liturgicalDay)
        assertEquals("마태오복음 25,1-13", g.reference)
        assertEquals("신랑이 온다. 신랑을 맞으러 나가라.", g.title)
        assertTrue(g.readings[0].text.startsWith("<지혜를 찾는 이들은 지혜를 만난다.>\n▥ 지혜서의 말씀입니다.6,12-16"))
        assertTrue(g.readings[2].text.contains("<또는>"))
        assertEquals("마태 24,42.44 참조", g.readings[3].text.lines().first())
    }

    @Test
    fun weekdayWithoutSecondReading() {
        val r = GospelRepository.readingsBeforeGospel(
            listOf("본기도" to "x", "제1독서" to "a", "화답송" to "b", "복음 환호송" to "c", "복음" to "d", "영성체송" to "e")
        )
        assertEquals(listOf("제1독서", "화답송", "복음 환호송"), r.map { it.kind })
    }

    @Test
    fun multipleGospelsInSingleSectionAndSequence() {
        val multiPage = """
            <html><body>
            <p class="m_t_today01">[(녹) 연중 제33주일]</p>
            ${section("제1독서", "▥ 잠언의 말씀입니다.31,10-13", "10 훌륭한 아내는 누가 찾을 수 있나?", "주님의 말씀입니다.")}
            ${section("화답송", "시편 128(127),1-5", "◎ 행복하여라.")}
            ${section("제2독서", "▥ 사도 바오로의 테살로니카 1서 말씀입니다.5,1-6", "1 형제 여러분,", "주님의 말씀입니다.")}
            ${section("부속가", "오소서 성령님.", "주님의 빛 그 빛살을 하늘에서 내리소서.")}
            ${section("복음 환호송", "요한 15,4.5 참조", "◎ 알렐루야.")}
            ${section(
                "복음",
                "&lt;긴 복음 주제&gt;",
                "✠ 마태오가 전한 거룩한 복음입니다.25,14-30",
                "14 하늘 나라는 어떤 사람이 여행을 떠나면서 종들을 불러 재산을 맡기는 것과 같다.",
                "주님의 말씀입니다.",
                "◎ 그리스도님, 찬미합니다.",
                "&lt;또는&gt;",
                "▥ 마태오가 전한 거룩한 복음입니다.25,14-15.19-21",
                "14 하늘 나라는 어떤 사람이 여행을 떠나면서 종들을 불렀다.",
                "주님의 말씀입니다.",
                "◎ 그리스도님, 찬미합니다.",
            )}
            </body></html>
        """.trimIndent()
        val g = GospelRepository.parseHtml(multiPage, LocalDate.of(2026, 11, 15), "u")
        assertEquals(listOf("제1독서", "화답송", "제2독서", "부속가", "복음 환호송"), g.readings.map { it.kind })
        assertEquals(2, g.allGospels.size)
        assertEquals("마태오복음 25,14-30", g.allGospels[0].reference)
        assertEquals("긴 복음 주제", g.allGospels[0].title)
        assertEquals("마태오복음 25,14-15.19-21", g.allGospels[1].reference)
        assertEquals("", g.allGospels[1].title)
        assertEquals("마태오복음 25,14-30", g.reference)
    }

    @Test
    fun palmSundayProcessionAndMultiplePassions() {
        val palmPage = """
            <html><body>
            <p class="m_t_today01">[(홍) 주님 수난 성지 주일]</p>
            ${section(
                "오늘 전례",
                "성지 축복과 행렬을 거행한다.",
                "╋ 마태오가 전한 거룩한 복음입니다. 21,1-11",
                "1 예수님과 제자들이 예루살렘에 가까이 이르러 벳파게에 다다랐을 때다.",
                "주님의 말씀입니다.",
            )}
            ${section("제1독서", "▥ 이사야서의 말씀입니다.50,4-7", "4 주 하느님께서는 나에게 제자의 혀를 주셨다.", "주님의 말씀입니다.")}
            ${section(
                "복음",
                "&lt;우리 주 예수 그리스도의 수난기&gt;",
                "○ 해설자 + 예수님 ◎ 군중",
                "○ 마태오가 전한 우리 주 예수 그리스도의 수난기입니다. 26,14─27.66",
                "14 그때에 열두 제자 가운데 하나로 유다 이스카리옷이라는 자가 갔다.",
                "주님의 말씀입니다.",
                "&lt;또는&gt;",
                "&lt;우리 주 예수 그리스도의 수난기&gt;",
                "○ 마태오가 전한 우리 주 예수 그리스도의 수난기입니다. 27,11-54",
                "11 예수님께서 총독 앞에 서 계셨을 때에 총독이 예수님께 물었다.",
                "○ 주님의 말씀입니다.",
                "◎ 그리스도님, 찬미합니다.",
            )}
            </body></html>
        """.trimIndent()
        val g = GospelRepository.parseHtml(palmPage, LocalDate.of(2026, 3, 29), "u")
        assertEquals(3, g.allGospels.size)
        assertEquals("마태오 수난기 26,14─27.66", g.allGospels[0].reference)
        assertEquals("마태오 수난기 27,11-54", g.allGospels[1].reference)
        assertTrue(!g.allGospels[1].body.contains("총독이 예수님께 물었다.\n○ 주님의 말씀입니다."))
        assertEquals("마태오복음 21,1-11", g.allGospels[2].reference)
        assertEquals("행렬 복음", g.allGospels[2].label)
    }

    @Test
    fun numberedSubReadingsSplitCleanly() {
        val vigilReadings = GospelRepository.readingsBeforeGospel(
            listOf(
                "제1독서" to "▥ 창세기 1,1",
                "제2독서" to "▥ 창세기 22,1\n제3독서\n▥ 탈출기 14,15\n제4독서\n▥ 이사야 54,5\n서간\n▥ 로마서 6,3",
                "복음 환호송" to "시편 118",
                "복음" to "✠ 마태오 28,1",
            )
        )
        assertEquals(listOf("제1독서", "제2독서", "제3독서", "제4독서", "서간", "복음 환호송"), vigilReadings.map { it.kind })
    }

    @Test
    fun goodFridayPassionWithoutHeader() {
        val goodFridayPage = """
            <html><body>
            <p class="m_t_today01">[(홍) 주님 수난 성금요일]</p>
            ${section("제1독서", "▥ 이사야서의 말씀입니다.52,13―53,12", "13 보라, 나의 종은 성공을 거두고", "주님의 말씀입니다.")}
            ${section(
                "복음",
                "&lt;우리 주 예수 그리스도의 수난기&gt;",
                "○ 해설자 + 예수님 ● 다른 한 사람 ▣ 다른 몇몇 사람 ◎ 군중",
                "그때에 1 예수님께서는 제자들과 함께 키드론 골짜기 건너편으로 가셨다.",
                "주님의 말씀입니다.",
                "◎ 그리스도님, 찬미합니다.",
            )}
            </body></html>
        """.trimIndent()
        val g = GospelRepository.parseHtml(goodFridayPage, LocalDate.of(2026, 4, 3), "u")
        assertEquals(1, g.allGospels.size)
        assertEquals("주님 수난기", g.reference)
        assertEquals("우리 주 예수 그리스도의 수난기", g.title)
        assertTrue(g.body.startsWith("그때에 1 예수님께서는"))
    }

    @Test
    fun hymnSearchByNumberAndTitle() {
        val list = listOf(Hymn(1, "나는 믿나이다", "연중"), Hymn(12, "주님의 기도", "연중"), Hymn(24, "내 맘의 천주여", "연중"), Hymn(120, "부활", "부활"))
        assertEquals(listOf(12, 120), HymnRepository.search(list, "12").map { it.number })
        assertEquals(listOf(24), HymnRepository.search(list, "24번").map { it.number })
        assertEquals(listOf(24), HymnRepository.search(list, "내맘의").map { it.number })
        assertEquals(4, HymnRepository.search(list, " ").size)
    }
}
