package com.hdlee73.dailyhabit.sync

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * PC ↔ 휴대폰 사이 약속.
 *
 * - 짝 맺기: PC가 보여 주는 QR(짝 맺기 코드)에 PC 주소와 새 비밀 키가 들어 있다.
 * - 모든 요청·응답 본문은 그 키로 AES-256-GCM 암호화한다. 키가 없으면 읽을 수도, 꾸밀 수도 없다.
 *   요청 시각을 함께 묶어 오래된 요청을 다시 보내는 것도 막는다.
 */
object SyncProtocol {
    const val SERVICE_TYPE = "_dailyhabit._tcp"
    const val DEFAULT_PORT = 47821
    /** 요청 시각이 이만큼 넘게 차이 나면 거절 */
    const val MAX_SKEW_MS = 10 * 60 * 1000L
    private val random = SecureRandom()

    fun newKey(): ByteArray = ByteArray(32).also { random.nextBytes(it) }
    fun newId(): String = ByteArray(6).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    fun b64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    fun unb64(s: String): ByteArray = Base64.getUrlDecoder().decode(s)

    fun seal(key: ByteArray, aad: String, plain: ByteArray): ByteArray {
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        c.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return nonce + c.doFinal(plain)
    }

    /** 키가 틀리거나 내용이 바뀌었으면 예외 */
    fun open(key: ByteArray, aad: String, data: ByteArray): ByteArray {
        require(data.size >= 12 + 16) { "too short" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, data, 0, 12))
        c.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return c.doFinal(data, 12, data.size - 12)
    }

    fun requestAad(method: String, path: String, device: String, time: Long) = "req|$method|$path|$device|$time"
    fun responseAad(method: String, path: String, device: String, time: Long) = "resp|$method|$path|$device|$time"

    fun edition(catholic: Boolean) = if (catholic) "catholic" else "general"
    fun editionLabel(edition: String) = if (edition == "catholic") "천주교인용" else "일반용"

    fun validPhotoName(name: String) = Regex("[A-Za-z0-9._-]{1,120}").matches(name) && !name.startsWith(".")
}

/** QR에 담는 짝 맺기 코드 */
data class PairingCode(
    val serverId: String,
    val edition: String,
    val hosts: List<String>,
    val port: Int,
    val key: ByteArray,
    val name: String,
) {
    fun encode(): String = "DH1;id=$serverId;ed=$edition;p=$port;h=${hosts.joinToString(",")};k=${SyncProtocol.b64(key)};n=" +
        URLEncoder.encode(name, "UTF-8")

    companion object {
        /** 형식이 틀리면 null */
        fun parse(text: String): PairingCode? = runCatching {
            val t = text.trim()
            if (!t.startsWith("DH1;")) return null
            val m = t.removePrefix("DH1;").split(';').associate { it.substringBefore('=') to it.substringAfter('=', "") }
            PairingCode(
                serverId = m.getValue("id"),
                edition = m.getValue("ed"),
                hosts = m.getValue("h").split(',').filter { it.isNotBlank() },
                port = m.getValue("p").toInt(),
                key = SyncProtocol.unb64(m.getValue("k")).also { require(it.size == 32) },
                name = URLDecoder.decode(m["n"].orEmpty(), "UTF-8"),
            )
        }.getOrNull()
    }
}

/** 동기화가 안 된 까닭 (사용자에게 그대로 보여 줄 문장) */
class SyncException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason { UNREACHABLE, UNPAIRED, PAIRING_CLOSED, EDITION, CLOCK, BAD_RESPONSE, OTHER }

    companion object {
        fun fromStatus(code: Int, body: String): SyncException = when (code) {
            410 -> SyncException(Reason.UNPAIRED, "PC에서 이 휴대폰의 연결이 해제됐어요. 다시 짝을 맺어 주세요")
            403 -> SyncException(Reason.PAIRING_CLOSED, "PC의 짝 맺기 시간이 지났어요. PC에서 QR 코드를 다시 띄워 주세요")
            409 -> SyncException(Reason.EDITION, "천주교인용은 천주교인용끼리, 일반용은 일반용끼리만 동기화할 수 있어요")
            425 -> SyncException(Reason.CLOCK, "휴대폰과 PC의 시계가 10분 넘게 달라요. 두 기기의 날짜·시간을 맞춰 주세요")
            else -> SyncException(Reason.OTHER, "PC가 요청을 처리하지 못했어요 ($code ${body.take(80)})")
        }
    }
}

/** 사진 파일 보관함 (맛집 방문 사진) */
interface PhotoStore {
    fun has(name: String): Boolean
    fun read(name: String): ByteArray?
    fun write(name: String, bytes: ByteArray)
}

// ───────────── PC 쪽 ─────────────

/** PC에 짝 맺은 휴대폰 */
data class PairedDevice(val id: String, val name: String, val key: ByteArray, val pairedAt: Long, val lastSync: Long = 0L)

interface DeviceStore {
    fun find(id: String): PairedDevice?
    fun save(device: PairedDevice)
}

/**
 * PC 쪽 요청 처리 (HTTP 서버와 상관없이 바이트만 다룬다 → 테스트에서 바로 부를 수 있다).
 * [pendingKey]: 지금 짝 맺기 QR을 띄워 둔 동안의 새 키 (없으면 null)
 */
class SyncServerCore(
    private val db: SyncDb,
    private val edition: String,
    private val serverId: String,
    private val serverName: String,
    private val devices: DeviceStore,
    private val photos: PhotoStore,
    private val pendingKey: () -> ByteArray?,
    private val onPaired: (PairedDevice) -> Unit = {},
    private val onSynced: (PairedDevice, Set<String>) -> Unit = { _, _ -> },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    class Response(val status: Int, val body: ByteArray)

    private fun text(status: Int, msg: String) = Response(status, msg.toByteArray(Charsets.UTF_8))

    /** [pathWithQuery] 예: /sync, /photo?n=p_1.jpg */
    fun handle(method: String, pathWithQuery: String, header: (String) -> String?, body: ByteArray): Response {
        val deviceId = header("X-DH-Device") ?: return text(400, "no device")
        val time = header("X-DH-Time")?.toLongOrNull() ?: return text(400, "no time")
        if (kotlin.math.abs(clock() - time) > SyncProtocol.MAX_SKEW_MS) return text(425, "clock")
        val path = pathWithQuery.substringBefore('?')
        val query = pathWithQuery.substringAfter('?', "").split('&').filter { it.isNotEmpty() }
            .associate { URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }
        val reqAad = SyncProtocol.requestAad(method, pathWithQuery, deviceId, time)
        val respAad = SyncProtocol.responseAad(method, pathWithQuery, deviceId, time)

        if (path == "/pair") {
            val key = pendingKey() ?: return text(403, "pairing closed")
            val plain = runCatching { SyncProtocol.open(key, reqAad, body) }.getOrNull() ?: return text(403, "pairing key")
            val req = JSONObject(String(plain, Charsets.UTF_8))
            if (req.optString("edition") != edition) return text(409, "edition")
            val device = PairedDevice(deviceId, req.optString("name", "휴대폰"), key, clock())
            devices.save(device)
            onPaired(device)
            val resp = JSONObject().put("serverId", serverId).put("name", serverName).put("edition", edition)
            return Response(200, SyncProtocol.seal(key, respAad, resp.toString().toByteArray(Charsets.UTF_8)))
        }

        val device = devices.find(deviceId) ?: return text(410, "unpaired")
        val plain = runCatching { SyncProtocol.open(device.key, reqAad, body) }.getOrNull() ?: return text(410, "bad key")
        fun ok(bytes: ByteArray) = Response(200, SyncProtocol.seal(device.key, respAad, bytes))

        return when {
            path == "/sync" && method == "POST" -> {
                val req = JSONObject(String(plain, Charsets.UTF_8))
                if (req.optString("edition") != edition) return text(409, "edition")
                val resp = SyncEngine.serve(db, req)
                // 휴대폰에 없는 사진을 알려 주려고 PC가 가진 사진 이름을 붙인다
                val names = db.tx { db.query("SELECT DISTINCT photo FROM restaurant_visits WHERE photo <> ''") }
                    .mapNotNull { it.str("photo") }.filter { SyncProtocol.validPhotoName(it) && photos.has(it) }
                resp.put("photos", JSONArray(names))
                val changed = resp.optJSONArray("changed")?.let { a -> List(a.length()) { a.getString(it) }.toSet() }.orEmpty()
                val updated = device.copy(lastSync = clock())
                devices.save(updated)
                onSynced(updated, changed)
                ok(resp.toString().toByteArray(Charsets.UTF_8))
            }
            path == "/photo/get" || path == "/photo/put" -> {
                val name = query["n"].orEmpty()
                if (!SyncProtocol.validPhotoName(name)) return text(400, "name")
                if (path == "/photo/get") photos.read(name)?.let { ok(it) } ?: text(404, "no photo")
                else { photos.write(name, plain); ok(ByteArray(0)) }
            }
            path == "/ping" -> ok(JSONObject().put("serverId", serverId).put("name", serverName).toString().toByteArray(Charsets.UTF_8))
            else -> text(404, "not found")
        }
    }
}

// ───────────── 휴대폰 쪽 ─────────────

/** 짝 맺은 PC */
data class PairedServer(
    val serverId: String,
    val name: String,
    val edition: String,
    val hosts: List<String>,
    val port: Int,
    val key: ByteArray,
    val deviceId: String,
)

/** HTTP로 PC에 요청을 보낸다 (java.net만 써서 안드로이드·PC 어디서나 돈다) */
class SyncClient(
    private val deviceId: String,
    private val key: ByteArray,
    private val clock: () -> Long = System::currentTimeMillis,
    private val connectTimeoutMs: Int = 3000,
    private val readTimeoutMs: Int = 60_000,
) {
    /** 응답 본문(복호화한 것). 실패하면 SyncException. 본문이 비어도 암호화해서 보내 누가 보냈는지 확인되게 한다 */
    fun call(host: String, port: Int, method: String, pathWithQuery: String, body: ByteArray?): ByteArray {
        val time = clock()
        val url = URI("http", null, host, port, pathWithQuery.substringBefore('?'), pathWithQuery.substringAfter('?', "").ifEmpty { null }, null).toURL()
        val conn = try {
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                useCaches = false
                setRequestProperty("X-DH-Device", deviceId)
                setRequestProperty("X-DH-Time", time.toString())
                setRequestProperty("Content-Type", "application/octet-stream")
            }
        } catch (e: Exception) {
            throw SyncException(SyncException.Reason.UNREACHABLE, "PC에 연결할 수 없어요 (${e.message})")
        }
        try {
            if (body != null) {
                conn.doOutput = true
                val sealed = SyncProtocol.seal(key, SyncProtocol.requestAad(method, pathWithQuery, deviceId, time), body)
                conn.setFixedLengthStreamingMode(sealed.size)
                conn.outputStream.use { it.write(sealed) }
            }
            val code = conn.responseCode
            if (code != 200) {
                val err = (conn.errorStream ?: conn.inputStream)?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty()
                if (code == 404 && pathWithQuery.startsWith("/photo/")) throw SyncException(SyncException.Reason.OTHER, "no photo")
                throw SyncException.fromStatus(code, err)
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            return runCatching { SyncProtocol.open(key, SyncProtocol.responseAad(method, pathWithQuery, deviceId, time), bytes) }
                .getOrElse { throw SyncException(SyncException.Reason.BAD_RESPONSE, "PC의 응답을 확인할 수 없어요. 다시 짝을 맺어 주세요") }
        } catch (e: SyncException) {
            throw e
        } catch (e: java.io.IOException) {
            throw SyncException(SyncException.Reason.UNREACHABLE, "PC에 연결할 수 없어요. 같은 와이파이인지, PC에서 DailyHabit이 켜져 있는지 확인해 주세요")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** QR 코드로 짝 맺기. 성공하면 PC 정보 */
        fun pair(code: PairingCode, deviceId: String, deviceName: String, edition: String): PairedServer {
            if (code.edition != edition) {
                throw SyncException(SyncException.Reason.EDITION, "이 QR은 ${SyncProtocol.editionLabel(code.edition)} PC 앱이에요. 같은 판본끼리만 동기화할 수 있어요")
            }
            val client = SyncClient(deviceId, code.key)
            val body = JSONObject().put("edition", edition).put("name", deviceName).toString().toByteArray(Charsets.UTF_8)
            var last: SyncException? = null
            for (h in code.hosts) {
                try {
                    val resp = JSONObject(String(client.call(h, code.port, "POST", "/pair", body), Charsets.UTF_8))
                    return PairedServer(
                        resp.optString("serverId", code.serverId), resp.optString("name", code.name), edition,
                        listOf(h) + code.hosts.filter { it != h }, code.port, code.key, deviceId,
                    )
                } catch (e: SyncException) {
                    last = e
                    if (e.reason != SyncException.Reason.UNREACHABLE) throw e
                }
            }
            throw last ?: SyncException(SyncException.Reason.UNREACHABLE, "PC 주소가 없어요")
        }
    }
}

/**
 * 휴대폰에서 한 번 동기화하기: 기록을 주고받고, 사진을 채운다.
 * [discover]: 저장된 주소로 안 될 때 같은 와이파이에서 PC를 찾아 (host, port)를 돌려준다 (못 찾으면 null)
 */
object SyncRunner {
    class Outcome(
        val server: PairedServer,
        val state: SyncEngine.ClientState,
        val sent: Int,
        val received: Int,
        val photosSent: Int,
        val photosReceived: Int,
        val changedTables: Set<String>,
    )

    fun run(
        db: SyncDb,
        photos: PhotoStore,
        server: PairedServer,
        state: SyncEngine.ClientState,
        discover: () -> Pair<String, Int>?,
    ): Outcome {
        val client = SyncClient(server.deviceId, server.key)
        var current = server
        var host = server.hosts.firstOrNull()
        var port = server.port

        fun <T> withHost(block: (String, Int) -> T): T {
            // 저장된 주소부터 차례로, 안 되면 와이파이에서 다시 찾는다
            val tried = HashSet<String>()
            var last: SyncException? = null
            val candidates = (listOfNotNull(host) + current.hosts).distinct()
            for (h in candidates) {
                tried += "$h:$port"
                try {
                    return block(h, port).also { host = h }
                } catch (e: SyncException) {
                    if (e.reason != SyncException.Reason.UNREACHABLE) throw e
                    last = e
                }
            }
            val found = discover()
            if (found != null && "${found.first}:${found.second}" !in tried) {
                host = found.first; port = found.second
                current = current.copy(hosts = (listOf(found.first) + current.hosts.filter { it != found.first }).take(4), port = found.second)
                return block(found.first, found.second)
            }
            throw last ?: SyncException(SyncException.Reason.UNREACHABLE, "PC를 찾지 못했어요")
        }

        var photoNames: Set<String> = emptySet()
        val result = withHost { h, p ->
            SyncEngine.clientSync(db, state) { req ->
                req.put("edition", current.edition)
                val resp = JSONObject(String(client.call(h, p, "POST", "/sync", req.toString().toByteArray(Charsets.UTF_8)), Charsets.UTF_8))
                photoNames = resp.optJSONArray("photos")?.let { a -> List(a.length()) { a.getString(it) }.toSet() }.orEmpty()
                resp
            }
        }

        // 사진: 이쪽에만 있는 것은 올리고, 기록에는 있는데 파일이 없는 것은 받아 온다
        var sent = 0
        var received = 0
        val referenced = db.tx { db.query("SELECT DISTINCT photo FROM restaurant_visits WHERE photo <> ''") }
            .mapNotNull { it.str("photo") }.filter { SyncProtocol.validPhotoName(it) }
        for (name in referenced) {
            val h = host ?: break
            runCatching {
                if (photos.has(name) && name !in photoNames) {
                    photos.read(name)?.let { client.call(h, port, "POST", "/photo/put?n=$name", it); sent++ }
                } else if (!photos.has(name) && name in photoNames) {
                    photos.write(name, client.call(h, port, "POST", "/photo/get?n=$name", ByteArray(0))); received++
                }
            }
        }
        return Outcome(current, result.state, result.sent, result.received, sent, received, result.changedTables)
    }
}
