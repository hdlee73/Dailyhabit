package com.hdlee73.dailyhabit.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.hdlee73.dailyhabit.BuildConfig
import com.hdlee73.dailyhabit.notify.Reminders
import com.hdlee73.dailyhabit.sync.PairedServer
import com.hdlee73.dailyhabit.sync.PairingCode
import com.hdlee73.dailyhabit.sync.PhotoStore
import com.hdlee73.dailyhabit.sync.SyncClient
import com.hdlee73.dailyhabit.sync.SyncEngine
import com.hdlee73.dailyhabit.sync.SyncException
import com.hdlee73.dailyhabit.sync.SyncProtocol
import com.hdlee73.dailyhabit.sync.SyncRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * PC와 같은 와이파이에서 직접 동기화 (휴대폰 쪽).
 * PC 앱이 보여 주는 QR로 한 번 짝을 맺고, 이후에는 저장된 주소 → 와이파이 검색(mDNS) 순으로 PC를 찾아 변경된 기록만 주고받는다.
 */
object PhoneSync {
    data class Status(
        val paired: Boolean,
        val pcName: String,
        val lastSync: Long,
        val message: String,
        val ok: Boolean,
        val running: Boolean,
    )

    private val mutex = Mutex()
    private val _status = MutableStateFlow<Status?>(null)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("sync", Context.MODE_PRIVATE)

    fun status(context: Context): StateFlow<Status?> {
        if (_status.value == null) _status.value = read(context, running = false)
        return _status
    }

    private fun read(context: Context, running: Boolean): Status {
        val p = prefs(context)
        return Status(
            paired = p.contains("key"),
            pcName = p.getString("name", "PC").orEmpty(),
            lastSync = p.getLong("lastSync", 0L),
            message = p.getString("message", "").orEmpty(),
            ok = p.getBoolean("ok", true),
            running = running,
        )
    }

    private fun deviceId(context: Context): String {
        val p = prefs(context)
        return p.getString("deviceId", null) ?: UUID.randomUUID().toString().replace("-", "").take(16).also {
            p.edit().putString("deviceId", it).apply()
        }
    }

    private fun server(context: Context): PairedServer? {
        val p = prefs(context)
        val key = p.getString("key", null) ?: return null
        return PairedServer(
            serverId = p.getString("serverId", "").orEmpty(),
            name = p.getString("name", "PC").orEmpty(),
            edition = SyncProtocol.edition(BuildConfig.CATHOLIC),
            hosts = p.getString("hosts", "").orEmpty().split(',').filter { it.isNotBlank() },
            port = p.getInt("port", SyncProtocol.DEFAULT_PORT),
            key = SyncProtocol.unb64(key),
            deviceId = deviceId(context),
        )
    }

    private fun saveServer(context: Context, s: PairedServer) {
        prefs(context).edit()
            .putString("serverId", s.serverId).putString("name", s.name)
            .putString("hosts", s.hosts.joinToString(",")).putInt("port", s.port)
            .putString("key", SyncProtocol.b64(s.key))
            .apply()
    }

    private fun photoStore(context: Context) = object : PhotoStore {
        private fun f(name: String) = File(File(context.filesDir, "photos").apply { mkdirs() }, name)
        override fun has(name: String) = f(name).exists()
        override fun read(name: String) = f(name).takeIf { it.exists() }?.readBytes()
        override fun write(name: String, bytes: ByteArray) {
            val tmp = f("$name.part")
            tmp.writeBytes(bytes)
            tmp.renameTo(f(name))
        }
    }

    private fun deviceName(): String =
        listOf(Build.MANUFACTURER, Build.MODEL).filter { !it.isNullOrBlank() }.joinToString(" ").ifBlank { "휴대폰" }.take(40)

    /** QR에서 읽은 글자로 짝을 맺고 바로 처음 동기화를 한다. 실패하면 사용자에게 보여 줄 문장 */
    suspend fun pair(context: Context, text: String): String? = withContext(Dispatchers.IO) {
        val code = PairingCode.parse(text) ?: return@withContext "DailyHabit PC 앱의 짝 맺기 QR 코드가 아니에요"
        try {
            val s = SyncClient.pair(code, deviceId(context), deviceName(), SyncProtocol.edition(BuildConfig.CATHOLIC))
            saveServer(context, s)
            // 새 PC와는 처음부터 모두 주고받는다
            prefs(context).edit().putLong("serverSeq", 0L).putLong("sentSeq", -1L).putLong("lastSync", 0L)
                .putString("message", "").putBoolean("ok", true).apply()
            _status.value = read(context, running = false)
            null
        } catch (e: SyncException) {
            e.message
        } catch (e: Exception) {
            "짝을 맺지 못했어요 (${e.message})"
        }.also { if (it == null) syncNow(context) }
    }

    fun unpair(context: Context) {
        prefs(context).edit().remove("key").remove("serverId").remove("hosts").remove("port").remove("name")
            .remove("serverSeq").remove("sentSeq").remove("lastSync").remove("message").apply()
        _status.value = read(context, running = false)
    }

    /**
     * 지금 동기화. 짝을 맺지 않았으면 아무것도 안 한다.
     * [quiet]이면 PC를 못 찾아도 오류로 남기지 않는다 (밖에 있을 때 자동 동기화).
     * 돌려주는 값: 결과 문장
     */
    suspend fun syncNow(context: Context, quiet: Boolean = false): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val ctx = context.applicationContext
            val s = server(ctx) ?: return@withContext "PC와 짝을 맺지 않았어요"
            _status.value = read(ctx, running = true)
            val p = prefs(ctx)
            val state = SyncEngine.ClientState(p.getLong("serverSeq", 0L), p.getLong("sentSeq", -1L))
            val db = AppDatabase.get(ctx)
            var message: String
            var ok = true
            try {
                val out = SyncRunner.run(AndroidSyncDb.of(db), photoStore(ctx), s, state) { discover(ctx, s.serverId) }
                saveServer(ctx, out.server)
                val parts = buildList {
                    if (out.received > 0) add("받음 ${out.received}")
                    if (out.sent > 0) add("보냄 ${out.sent}")
                    if (out.photosSent + out.photosReceived > 0) add("사진 ${out.photosSent + out.photosReceived}")
                }
                message = if (parts.isEmpty()) "이미 같아요" else parts.joinToString(" · ")
                p.edit().putLong("serverSeq", out.state.serverSeq).putLong("sentSeq", out.state.sentSeq)
                    .putLong("lastSync", System.currentTimeMillis()).apply()
                if (out.received > 0) Reminders.rescheduleAll(ctx)
            } catch (e: SyncException) {
                ok = false
                message = e.message ?: "동기화하지 못했어요"
                if (quiet && e.reason == SyncException.Reason.UNREACHABLE) {
                    _status.value = read(ctx, running = false)
                    return@withContext message
                }
            } catch (e: Exception) {
                ok = false
                message = "동기화하지 못했어요 (${e.message})"
            }
            p.edit().putString("message", message).putBoolean("ok", ok).apply()
            _status.value = read(ctx, running = false)
            message
        }
    }

    /** 같은 와이파이에서 PC 앱을 찾는다 (최대 8초). 못 찾으면 null */
    private fun discover(context: Context, serverId: String): Pair<String, Int>? {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
        val found = CountDownLatch(1)
        var result: Pair<String, Int>? = null
        val resolving = java.util.concurrent.atomic.AtomicBoolean(false)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { found.countDown() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceName.startsWith("DH-$serverId")) return
                if (!resolving.compareAndSet(false, true)) return
                @Suppress("DEPRECATION")
                nsd.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { resolving.set(false) }
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        val host = serviceInfo.host?.hostAddress
                        if (host != null && !host.contains(':')) {
                            result = host to serviceInfo.port
                            found.countDown()
                        } else resolving.set(false)
                    }
                })
            }
        }
        return try {
            nsd.discoverServices(SyncProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            found.await(8, TimeUnit.SECONDS)
            result
        } catch (e: Exception) {
            null
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }
}
