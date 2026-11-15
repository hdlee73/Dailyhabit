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
    /** 복음 앞의 말씀 전례 (제1독서 · 화답송 · 제2독서 · 복음 환호송 등), 미사 순서대로 */
    val readings: List<Reading> = emptyList(),
    /** 복음이 둘 이상인 날(긴/짧은 복음, 행렬 복음, 밤/낮 미사 등) 모두 포함 */
    val gospels: List<GospelEntry> = emptyList(),
) {
    val allGospels: List<GospelEntry>
        get() = gospels.ifEmpty { listOf(GospelEntry(title, reference, body)) }
}

/** 복음 한 편 */
data class GospelEntry(
    val title: String,
    val reference: String,
    val body: String,
    val label: String = "",
    val massName: String = "",
)

/** 복음 앞의 말씀 전례 한 부분. [text]는 매일미사 본문 그대로 (줄 단위) */
data class Reading(val kind: String, val text: String, val massName: String = "")

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
                    (0 until a.length()).map {
                        a.getJSONObject(it).let { r ->
                            Reading(r.getString("kind"), r.getString("text"), r.optString("mass"))
                        }
                    }
                }.orEmpty(),
                gospels = o.optJSONArray("gospels")?.let { a ->
                    (0 until a.length()).map {
                        a.getJSONObject(it).let { g ->
                            GospelEntry(
                                title = g.optString("title"),
                                reference = g.optString("ref"),
                                body = g.getString("body"),
                                label = g.optString("label"),
                                massName = g.optString("mass"),
                            )
                        }
                    }
                }.orEmpty(),
            )
        }.getOrNull()
    }

    /** v1.10.1 이전에 저장된 캐시에는 다중 독서·복음 목록("gospels")이 없으므로 한 번 다시 받는다 */
    private fun hasLatestFormat(date: LocalDate): Boolean =
        prefs.getString(date.toString(), null)?.contains("\"gospels\"") == true

    suspend fun get(date: LocalDate, forceRefresh: Boolean = false): Result<Gospel> = withContext(Dispatchers.IO) {
        if (!forceRefresh && hasLatestFormat(date)) cached(date)?.let { return@withContext Result.success(it) }
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
                g.readings.forEach {
                    put(JSONObject().put("kind", it.kind).put("text", it.text).apply {
                        if (it.massName.isNotBlank()) put("mass", it.massName)
                    })
                }
            })
            .put("gospels", JSONArray().apply {
                g.allGospels.forEach {
                    put(JSONObject().put("title", it.title).put("ref", it.reference).put("body", it.body).apply {
                        if (it.label.isNotBlank()) put("label", it.label)
                        if (it.massName.isNotBlank()) put("mass", it.massName)
                    })
                }
            })
        prefs.edit().apply {
            // 최근 14일치만 보관
            prefs.all.keys.filter { runCatching { LocalDate.parse(it) }.getOrNull()?.isBefore(g.date.minusDays(14)) == true }
                .forEach { remove(it) }
            putString(g.date.toString(), o.toString())
        }.apply()
    }

    private fun connectHtml(url: String): String =
        Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 14) DailyHabit/1.0")
            .timeout(15_000)
            .get()
            .outerHtml()

    private fun fetch(date: LocalDate): Gospel {
        val url = sourceUrl(date)
        val html = connectHtml(url)
        val doc = Jsoup.parse(html, url)
        val extraLinks = extraMassLinks(doc, date, html)
        val main = parseHtml(html, date, url)
        if (extraLinks.isEmpty()) return main

        val mainMass = extractMassName(main.liturgicalDay)
        val allReadings = main.readings.map { it.copy(massName = mainMass) }.toMutableList()
        val allGospels = main.allGospels.map { it.copy(massName = mainMass) }.toMutableList()

        for ((extraUrl, fallbackMass) in extraLinks) {
            runCatching {
                val extraHtml = connectHtml(extraUrl)
                val extra = parseHtml(extraHtml, date, extraUrl)
                val massName = extractMassName(extra.liturgicalDay).ifBlank { fallbackMass }
                allReadings += extra.readings.map { it.copy(massName = massName) }
                allGospels += extra.allGospels.map { it.copy(massName = massName) }
            }
        }
        return main.copy(readings = allReadings, gospels = allGospels)
    }

    companion object {
        private val HANGUL = Regex("[가-힣]")
        private val MISSA_ID = Regex("missaid=(\\d+)")
        private val CUR_MISSA_ID = Regex("ctxtCurMid=(\\d+)")
        private val SUB_READING_HEADER = Regex("^(제\\d+독서|서간)$")

        private fun String.toLines(): List<String> =
            replace('\u00a0', ' ').lines().map { it.trim() }.filter { it.isNotEmpty() }

        private fun extractMassName(liturgicalDay: String): String {
            val clean = liturgicalDay.replace(Regex("^\\([^)]*\\)\\s*"), "").trim()
            return clean.substringAfter(" - ", clean).trim()
        }

        /** 같은 날짜에 미사가 여러 대(성탄 밤·새벽·낮, 부활 성야·낮, 위령 1·2·3미사 등)일 때 나머지 미사 URL을 찾는다 */
        private fun extraMassLinks(doc: org.jsoup.nodes.Document, date: LocalDate, html: String): List<Pair<String, String>> {
            val curId = CUR_MISSA_ID.find(html)?.groupValues?.get(1)
            val seenIds = linkedSetOf<String>()
            val links = mutableListOf<Triple<String, String, String>>()
            doc.select("a[href*=\"gomonth=$date\"]").forEach { a ->
                val text = a.text().trim()
                if (!HANGUL.containsMatchIn(text)) return@forEach
                val href = a.attr("href")
                val id = MISSA_ID.find(href)?.groupValues?.get(1) ?: return@forEach
                if (!seenIds.add(id)) return@forEach
                val abs = a.absUrl("href").ifBlank {
                    if (href.startsWith("?")) "https://maria.catholic.or.kr/mi_pr/missa/missa.asp$href" else href
                }
                links += Triple(id, abs, extractMassName(text))
            }
            if (links.size <= 1) return emptyList()
            val primaryId = curId ?: links.first().first
            return links.filter { it.first != primaryId }.map { it.second to it.third }
        }

        /** 매일미사 페이지 HTML → 복음(들)과 독서(들) */
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

            val mainGospels = sections
                .filter { it.first.replace(" ", "") == "복음" }
                .flatMap { (_, secText) ->
                    val secLines = secText.toLines()
                    parseGospels(secLines).ifEmpty { listOfNotNull(parsePassionSection(secLines)) }
                }
            val processionGospels = sections
                .filter { it.first.replace(" ", "") == "오늘전례" }
                .flatMap { (_, secText) -> parseGospels(secText.toLines(), label = "행렬 복음") }

            val allGospels = (mainGospels + processionGospels).ifEmpty { parseGospels(lines) }
            require(allGospels.isNotEmpty()) { "복음 본문을 찾지 못했습니다" }
            val primary = allGospels.first()
            val day = today.ifBlank { lines.firstNotNullOfOrNull { DAY.find(it)?.value }.orEmpty() }
            return Gospel(
                date = date,
                liturgicalDay = day,
                title = primary.title,
                reference = primary.reference,
                body = primary.body,
                sourceUrl = url,
                readings = readingsBeforeGospel(sections),
                gospels = allGospels,
            )
        }

        /** 말씀 전례 부분 이름 → 앱에서 쓰는 이름 */
        private fun readingKind(header: String): String? {
            val h = header.replace(" ", "")
            val numbered = Regex("^제(\\d+)독서").find(h)
            return when {
                numbered != null -> "제${numbered.groupValues[1]}독서"
                h.startsWith("독서") -> "독서"
                h.startsWith("서간") -> "서간"
                h.startsWith("화답송") -> "화답송"
                h.startsWith("부속가") -> "부속가"
                h.contains("환호송") || h.startsWith("복음전") -> "복음 환호송"
                else -> null
            }
        }

        /** 한 상자 안에 제3독서~제7독서·서간이 이어서 들어 있는 경우(파스카 성야 등) 각각 나눈다 */
        private fun splitSubReadings(kind: String, text: String): List<Reading> {
            val lines = text.lines()
            if (lines.none { SUB_READING_HEADER.matches(it.trim()) }) {
                return listOf(Reading(kind, text))
            }
            val out = ArrayList<Reading>()
            var currentKind = kind
            val buf = ArrayList<String>()
            for (raw in lines) {
                val t = raw.trim()
                if (SUB_READING_HEADER.matches(t)) {
                    val chunk = buf.joinToString("\n").trim()
                    if (chunk.isNotEmpty()) out.add(Reading(currentKind, chunk))
                    buf.clear()
                    currentKind = t
                } else {
                    buf.add(raw)
                }
            }
            val tail = buf.joinToString("\n").trim()
            if (tail.isNotEmpty()) out.add(Reading(currentKind, tail))
            return out
        }

        /**
         * (제목, 본문) 목록에서 독서·화답송·부속가·환호송을 미사 순서대로 모두 고른다.
         */
        fun readingsBeforeGospel(sections: List<Pair<String, String>>): List<Reading> {
            val out = ArrayList<Reading>()
            for ((header, body) in sections) {
                val kind = readingKind(header) ?: continue
                if (body.isNotBlank()) out.addAll(splitSubReadings(kind, body))
            }
            return out
        }

        private val START = Regex("^[✠╋▥○]?\\s*(\\S+?)(?:가|이) 전한 (거룩한 복음|우리 주 예수 그리스도의 수난기)입니다\\.?\\s*(.*)")
        private const val END = "주님의 말씀입니다"
        private val DAY = Regex("^\\((백|홍|녹|자|흑|장미)\\)\\s*(.+)")

        private fun parseGospels(lines: List<String>, label: String = ""): List<GospelEntry> {
            val out = ArrayList<GospelEntry>()
            var cursor = 0
            var minTitleSearch = 0
            while (cursor < lines.size) {
                val startIdx = (cursor until lines.size).firstOrNull { START.containsMatchIn(lines[it]) } ?: break
                val endIdx = (startIdx + 1 until lines.size).firstOrNull {
                    lines[it].removePrefix("○").trim().startsWith(END)
                } ?: lines.size
                val m = START.find(lines[startIdx])!!
                val evangelist = m.groupValues[1].trim().trimStart('✠', '╋', '▥', '○').trim()
                val isPassion = m.groupValues[2].contains("수난기")
                var reference = m.groupValues[3].trim()
                var bodyStart = startIdx + 1
                if (reference.isEmpty() && bodyStart < endIdx && Regex("^\\d+[,.:]\\d").containsMatchIn(lines[bodyStart])) {
                    reference = lines[bodyStart]
                    bodyStart++
                }
                val body = lines.subList(bodyStart, endIdx).joinToString("\n").trim()
                if (body.length > 20) {
                    val title = (startIdx - 1 downTo maxOf(minTitleSearch, startIdx - 4))
                        .map { lines[it] }
                        .firstOrNull { it.startsWith("<") && it.endsWith(">") && it != "<또는>" }
                        ?.removeSurrounding("<", ">")
                        .orEmpty()
                    val kindSuffix = if (isPassion) " 수난기" else "복음"
                    val fullRef = listOf(evangelist + kindSuffix, reference).filter { it.isNotBlank() }.joinToString(" ")
                    out.add(GospelEntry(title = title, reference = fullRef, body = body, label = label))
                }
                cursor = if (endIdx < lines.size) endIdx + 1 else lines.size
                minTitleSearch = cursor
            }
            return out
        }

        /** 성금요일처럼 '전한 … 수난기입니다' 머리글 없이 수난기 본문이 바로 시작하는 복음 섹션 */
        private fun parsePassionSection(lines: List<String>): GospelEntry? {
            val titleIdx = lines.indexOfFirst { it.contains("수난기") && it.startsWith("<") && it.endsWith(">") }
            if (titleIdx < 0) return null
            val endIdx = (titleIdx + 1 until lines.size).firstOrNull {
                lines[it].removePrefix("○").trim().startsWith(END)
            } ?: lines.size
            var bodyStart = titleIdx + 1
            while (bodyStart < endIdx && lines[bodyStart].startsWith("○") && lines[bodyStart].contains("해설자")) {
                bodyStart++
            }
            val body = lines.subList(bodyStart, endIdx).joinToString("\n").trim()
            if (body.length <= 20) return null
            return GospelEntry(
                title = lines[titleIdx].removeSurrounding("<", ">"),
                reference = "주님 수난기",
                body = body,
            )
        }

        fun parse(lines: List<String>, date: LocalDate, url: String): Gospel {
            val allGospels = parseGospels(lines)
            require(allGospels.isNotEmpty()) { "복음 본문을 찾지 못했습니다" }
            val primary = allGospels.first()
            val day = lines.firstNotNullOfOrNull { DAY.find(it)?.value }.orEmpty()
            return Gospel(
                date = date,
                liturgicalDay = day,
                title = primary.title,
                reference = primary.reference,
                body = primary.body,
                sourceUrl = url,
                gospels = allGospels,
            )
        }
    }
}
