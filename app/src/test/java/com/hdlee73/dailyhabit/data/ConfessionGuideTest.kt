package com.hdlee73.dailyhabit.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfessionGuideTest {
    @Test
    fun uniqueConscienceItemIds() {
        val total = ConfessionGuide.groups.sumOf { it.items.size }
        assertTrue(ConfessionGuide.groups.size >= 10)
        assertTrue(total >= 45)
        assertEquals(total, ConfessionGuide.allItemIds.size)
        assertTrue(ConfessionGuide.groups.any { it.subtitle == "십계명 외" })
        assertTrue(ConfessionGuide.groups.all { g -> g.items.all { it.text.isNotBlank() } })
    }

    @Test
    fun elapsedAndOpeningFormatting() {
        val today = LocalDate.of(2026, 11, 14)
        assertEquals(0L, ConfessionGuide.daysSince(today, today))
        assertEquals(0L, ConfessionGuide.daysSince(today.plusDays(3), today))
        assertEquals("오늘", ConfessionGuide.formatElapsed(0L))
        assertEquals("어제", ConfessionGuide.formatElapsed(1L))
        assertEquals("14일 지남", ConfessionGuide.formatElapsed(14L))
        assertTrue(ConfessionGuide.formatElapsed(65L).contains("약 2개월"))
        assertTrue(ConfessionGuide.formatElapsed(400L).contains("약 1년"))

        assertTrue(ConfessionGuide.confessionOpening(null, today).contains("○○일(개월)"))
        assertTrue(ConfessionGuide.confessionOpening(today.minusDays(21), today).contains("약 3주(21일)"))
        assertTrue(ConfessionGuide.confessionOpening(today.minusDays(90), today).contains("약 3개월"))
    }
}
