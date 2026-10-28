package com.hdlee73.dailyhabit.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 가톨릭 성가 목록의 한 곡 (번호 · 제목 · 전례 시기) */
data class Hymn(val number: Int, val title: String, val season: String)

/** 성가 한 곡의 가사와 악보 주소 */
data class HymnDetail(
    val number: Int,
    val lyrics: String,
    /** 단성(멜로디) 악보, 혼성(4부) 악보 이미지 주소 */
    val monoUrl: String,
    val mixedUrl: String,
)

/**
 * 가톨릭 성가. 번호와 제목 목록만 앱에 들어 있고(천주교인용 assets/hymns.tsv),
 * 가사와 악보는 굿뉴스 가톨릭 성가(maria.catholic.or.kr/sungga)에서 처음 열 때 받아 휴대폰에 저장한다.
 * 한 번 본 곡은 인터넷 없이도 다시 볼 수 있다.
 */
class HymnRepository(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "hymns").apply { mkdirs() }

    val all: List<Hymn> by lazy {
        runCatching {
            app.assets.open("hymns.tsv").bufferedReader().readLines().mapNotNull { line ->
                val f = line.split('\t')
                val n = f.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                Hymn(n, f.getOrNull(1).orEmpty(), f.getOrNull(2).orEmpty())
            }
        }.getOrDefault(emptyList())
    }

    val seasons: List<String> get() = all.map { it.season }.filter { it.isNotBlank() }.distinct()

    fun byNumber(n: Int): Hymn? = all.firstOrNull { it.number == n }

    fun pageUrl(n: Int) = "$BASE/sungga/search/sungga_view.asp?ctxtIndex=$n"

    fun isSaved(n: Int): Boolean = File(dir, "$n.json").exists()

    suspend fun detail(n: Int, forceRefresh: Boolean = false): Result<HymnDetail> = withContext(Dispatchers.IO) {
        val f = File(dir, "$n.json")
        if (!forceRefresh) readSaved(f)?.let { return@withContext Result.success(it) }
        runCatching { fetch(n) }
            .onSuccess { d ->
                f.writeText(
                    JSONObject().put("lyrics", d.lyrics).put("mono", d.monoUrl).put("mixed", d.mixedUrl).toString()
                )
            }
            .recoverCatching { err -> readSaved(f) ?: throw err }
    }

    private fun readSaved(f: File): HymnDetail? = if (!f.exists()) null else runCatching {
        val o = JSONObject(f.readText())
        HymnDetail(f.nameWithoutExtension.toInt(), o.optString("lyrics"), o.optString("mono"), o.optString("mixed"))
    }.getOrNull()

    /** 악보 이미지. 받은 것은 저장해 두고 다시 쓴다 */
    suspend fun score(n: Int, url: String, mono: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        val f = File(dir, "${n}_${if (mono) "mono" else "mixed"}.jpg")
        if (!f.exists() || f.length() == 0L) {
            runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 15_000
                c.readTimeout = 20_000
                c.setRequestProperty("User-Agent", UA)
                c.setRequestProperty("Referer", pageUrl(n))
                c.inputStream.use { input -> File(dir, f.name + ".part").also { p -> p.outputStream().use { input.copyTo(it) } }.renameTo(f) }
                c.disconnect()
            }
        }
        if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    private fun fetch(n: Int): HymnDetail {
        val doc = Jsoup.connect(pageUrl(n)).userAgent(UA).timeout(15_000).get()
        doc.select("br").forEach { it.after("\n") }
        val lyrics = doc.selectFirst("td.song_txt")?.wholeText().orEmpty()
            .replace(' ', ' ')
            .lines().map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString("\n")
        fun img(alt: String) = doc.select("img[src*=/sungga/Files/img/]").firstOrNull { it.attr("alt").contains(alt) }
            ?.attr("src")?.let { if (it.startsWith("http")) it else BASE + it }.orEmpty()
        val mono = img("단성")
        val mixed = img("혼성")
        require(lyrics.isNotBlank() || mono.isNotBlank() || mixed.isNotBlank()) { "성가 ${n}번을 찾지 못했습니다" }
        return HymnDetail(n, lyrics, mono, mixed)
    }

    companion object {
        private const val BASE = "https://maria.catholic.or.kr"
        private const val UA = "Mozilla/5.0 (Linux; Android 14) DailyHabit/1.0"

        /** 번호(정확히) 또는 제목(띄어쓰기 무시)으로 찾기 */
        fun search(list: List<Hymn>, query: String): List<Hymn> {
            val q = query.trim().removeSuffix("번")
            if (q.isEmpty()) return list
            q.toIntOrNull()?.let { num ->
                // 숫자는 그 번호를 맨 앞에, 그 숫자로 시작하는 번호를 뒤에
                return list.filter { it.number == num } +
                    list.filter { it.number != num && it.number.toString().startsWith(q) }
            }
            val key = q.replace(" ", "")
            return list.filter { it.title.replace(" ", "").contains(key) }
        }
    }
}
