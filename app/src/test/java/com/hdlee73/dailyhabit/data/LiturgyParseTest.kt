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
    fun hymnSearchByNumberAndTitle() {
        val list = listOf(Hymn(1, "나는 믿나이다", "연중"), Hymn(12, "주님의 기도", "연중"), Hymn(24, "내 맘의 천주여", "연중"), Hymn(120, "부활", "부활"))
        assertEquals(listOf(12, 120), HymnRepository.search(list, "12").map { it.number })
        assertEquals(listOf(24), HymnRepository.search(list, "24번").map { it.number })
        assertEquals(listOf(24), HymnRepository.search(list, "내맘의").map { it.number })
        assertEquals(4, HymnRepository.search(list, " ").size)
    }
}
