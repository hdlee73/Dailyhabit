package com.hdlee73.dailyhabit.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.time.LocalDate

data class Gospel(
    val date: LocalDate,
    val liturgicalDay: String,
    val title: String,
    val reference: String,
    val body: String,
    val sourceUrl: String,
    /** 복음 앞의 말씀 전례 (제1독서 · 화답송 · 제2독서 · 복음 환호송), 미사 순서대로 */
    val readings: List<Reading> = emptyList(),
)

/** 복음 앞의 말씀 전례 한 부분. [text]는 매일미사 본문 그대로 (줄 단위) */
data class Reading(val kind: String, val text: String)

/**
 * 한국천주교주교회의 '매일미사'(maria.catholic.or.kr)에서 오늘의 복음과 독서·화답송·복음 환호송을 가져온다.
 * 성공한 결과는 날짜별로 저장해 두어 오프라인에서도 다시 볼 수 있다.
 */
class GospelRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("gospel_cache", Context.MODE_PRIVATE)

    fun sourceUrl(date: LocalDate): String =
        "https://maria.catholic.or.kr/mi_pr/missa/missa.asp?menu=missa&gomonth=$date&missatype=DA"

    fun cached(date: LocalDate): Gospel? {
        val raw = prefs.getString(date.toString(), null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            Gospel(
                date = date,
                liturgicalDay = o.optString("day"),
                title = o.optString("title"),
                reference = o.optString("ref"),
                body = o.getString("body"),
                sourceUrl = o.optString("url", sourceUrl(date)),
                readings = o.optJSONArray("readings")?.let { a ->
                    (0 until a.length()).map { a.getJSONObject(it).let { r -> Reading(r.getString("kind"), r.getString("text")) } }
                }.orEmpty(),
            )
        }.getOrNull()
    }

    /** v1.9 이전에 저장된 캐시에는 독서가 없으므로 한 번 다시 받는다 */
    private fun hasReadings(date: LocalDate): Boolean =
        prefs.getString(date.toString(), null)?.contains("\"readings\"") == true

    suspend fun get(date: LocalDate, forceRefresh: Boolean = false): Result<Gospel> = withContext(Dispatchers.IO) {
        if (!forceRefresh && hasReadings(date)) cached(date)?.let { return@withContext Result.success(it) }
        runCatching { fetch(date) }
            .onSuccess { save(it) }
            .recoverCatching { err -> cached(date) ?: throw err }
    }

    private fun save(g: Gospel) {
        val o = JSONObject()
            .put("day", g.liturgicalDay)
            .put("title", g.title)
            .put("ref", g.reference)
            .put("body", g.body)
            .put("url", g.sourceUrl)
            .put("readings", JSONArray().apply {
                g.readings.forEach { put(JSONObject().put("kind", it.kind).put("text", it.text)) }
            })
        prefs.edit().apply {
            // 최근 14일치만 보관
            prefs.all.keys.filter { runCatching { LocalDate.parse(it) }.getOrNull()?.isBefore(g.date.minusDays(14)) == true }
                .forEach { remove(it) }
            putString(g.date.toString(), o.toString())
        }.apply()
    }

    private fun fetch(date: LocalDate): Gospel {
        val url = sourceUrl(date)
        val html = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 14) DailyHabit/1.0")
            .timeout(15_000)
            .get()
            .outerHtml()
        return parseHtml(html, date, url)
    }

    companion object {
        private fun String.toLines(): List<String> =
            replace('\u00a0', ' ').lines().map { it.trim() }.filter { it.isNotEmpty() }

        /** 매일미사 페이지 HTML → 복음과 그 앞의 독서들 */
        fun parseHtml(html: String, date: LocalDate, url: String): Gospel {
            val doc = Jsoup.parse(html)
            // 줄바꿈을 보존한 순수 텍스트로 변환
            doc.select("br").forEach { it.after("\n") }
            doc.select("p, div, h1, h2, h3, h4, li, tr").forEach { it.after("\n") }
            val lines = doc.body().wholeText().toLines()
            // 제목(h3.bd_tit) 바로 다음 상자에 각 부분의 본문이 있다
            val sections = doc.select("h3.bd_tit").mapNotNull { h ->
                val box = h.nextElementSibling() ?: return@mapNotNull null
                h.text().trim() to box.wholeText().toLines().joinToString("\n")
            }
            // 그날의 전례일 ([(녹) 연중 제32주일]). 페이지 위쪽 달력 목록의 다른 날과 헷갈리지 않게 따로 읽는다
            val today = doc.selectFirst("p.m_t_today01")?.text()?.trim()?.removeSurrounding("[", "]").orEmpty()
            val g = parse(lines, date, url)
            return g.copy(
                liturgicalDay = today.ifBlank { g.liturgicalDay },
                readings = readingsBeforeGospel(sections),
            )
        }

        /** 말씀 전례 부분 이름 → 앱에서 쓰는 이름 */
        private fun readingKind(header: String): String? {
            val h = header.replace(" ", "")
            return when {
                h.startsWith("제1독서") -> "제1독서"
                h.startsWith("제2독서") -> "제2독서"
                h.startsWith("화답송") -> "화답송"
                h.contains("환호송") || h.startsWith("복음전") -> "복음 환호송"
                else -> null
            }
        }

        /**
         * (제목, 본문) 목록에서 마지막 '복음' 바로 앞의 독서·화답송·환호송을 미사 순서대로 고른다.
         * 미사가 여럿인 날에도 복음과 같은 미사의 것만 나온다.
         */
        fun readingsBeforeGospel(sections: List<Pair<String, String>>): List<Reading> {
            val gospelIdx = sections.indexOfLast { it.first.replace(" ", "") == "복음" }
            if (gospelIdx < 0) return emptyList()
            val out = ArrayList<Reading>()
            var i = gospelIdx - 1
            while (i >= 0) {
                val kind = readingKind(sections[i].first) ?: break
                if (sections[i].second.isNotBlank()) out.add(0, Reading(kind, sections[i].second))
                i--
            }
            return out
        }

        private val START = Regex("✠?\\s*(\\S+?)(?:가|이) 전한 거룩한 복음입니다\\.?\\s*(.*)")
        private const val END = "주님의 말씀입니다"
        private val DAY = Regex("^\\((백|홍|녹|자|흑|장미)\\)\\s*(.+)")

        fun parse(lines: List<String>, date: LocalDate, url: String): Gospel {
            val startIdx = lines.indexOfLast { START.containsMatchIn(it) }
            require(startIdx >= 0) { "복음 본문을 찾지 못했습니다" }
            val endIdx = (startIdx + 1 until lines.size).firstOrNull { lines[it].startsWith(END) }
                ?: lines.size
            val m = START.find(lines[startIdx])!!
            val evangelist = m.groupValues[1].trim().removePrefix("✠").trim()
            var reference = m.groupValues[2].trim()
            var bodyStart = startIdx + 1
            if (reference.isEmpty() && bodyStart < endIdx && Regex("^\\d+[,.:]\\d").containsMatchIn(lines[bodyStart])) {
                reference = lines[bodyStart]
                bodyStart++
            }
            val body = lines.subList(bodyStart, endIdx)
                .joinToString("\n")
                .trim()
            require(body.length > 20) { "복음 본문이 너무 짧습니다" }

            // 복음 바로 위의 <...> 형태 주제 문장
            val title = (startIdx - 1 downTo maxOf(0, startIdx - 4))
                .map { lines[it] }
                .firstOrNull { it.startsWith("<") && it.endsWith(">") }
                ?.removeSurrounding("<", ">")
                .orEmpty()
            val day = lines.firstNotNullOfOrNull { DAY.find(it)?.value }.orEmpty()
            return Gospel(
                date = date,
                liturgicalDay = day,
                title = title,
                reference = listOf(evangelist + "복음", reference).filter { it.isNotBlank() }.joinToString(" "),
                body = body,
                sourceUrl = url,
            )
        }
    }
}
