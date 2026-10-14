package com.hdlee73.dailyhabit.sync

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/** PC 앱 안의 작은 HTTP 서버. 요청은 SyncServerCore가 처리한다 */
class SyncHttpServer(private val core: SyncServerCore, private val port: Int) {
    private var server: HttpServer? = null

    val boundPort: Int get() = server?.address?.port ?: port

    /** 포트가 이미 쓰이고 있으면 예외 */
    fun start() {
        val s = HttpServer.create(InetSocketAddress(port), 16)
        s.createContext("/") { ex ->
            try {
                val body = ex.requestBody.use { it.readNBytes(64 * 1024 * 1024) }
                val uri = ex.requestURI
                val path = uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: "")
                val resp = core.handle(ex.requestMethod, path, { ex.requestHeaders.getFirst(it) }, body)
                ex.responseHeaders.add("Content-Type", "application/octet-stream")
                ex.sendResponseHeaders(resp.status, if (resp.body.isEmpty()) -1 else resp.body.size.toLong())
                if (resp.body.isNotEmpty()) ex.responseBody.use { it.write(resp.body) }
            } catch (e: Throwable) {
                runCatching {
                    val msg = (e.message ?: "error").toByteArray(Charsets.UTF_8)
                    ex.sendResponseHeaders(500, msg.size.toLong())
                    ex.responseBody.use { it.write(msg) }
                }
            } finally {
                ex.close()
            }
        }
        // 한 번에 하나씩 처리해 같은 기록을 동시에 고치지 않게 한다
        s.executor = Executors.newSingleThreadExecutor { r -> Thread(r, "sync-http").apply { isDaemon = true } }
        s.start()
        server = s
    }

    fun stop() {
        server?.stop(0)
        server = null
    }
}
