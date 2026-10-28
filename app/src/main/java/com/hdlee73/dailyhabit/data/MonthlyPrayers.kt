package com.hdlee73.dailyhabit.data

import java.time.LocalDate

/** 성월(聖月): 한국 교회가 달마다 특별히 공경하는 신비와 그달에 바치는 기도 */
data class MonthlyPrayer(val month: Int, val name: String, val prayer: PrayerItem)

object MonthlyPrayers {
    const val CATEGORY = "성월 기도"

    val all: List<MonthlyPrayer> get() = MONTHLY_PRAYER_TEXTS

    /** 이달이 성월이면 그 성월 (3·5·6·9·10·11월) */
    fun forDate(date: LocalDate): MonthlyPrayer? = all.firstOrNull { it.month == date.monthValue }
}
