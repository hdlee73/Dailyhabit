package com.hdlee73.dailyhabit.sync

import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 실제 HTTP 서버(PC)와 클라이언트(휴대폰) 코드를 이 컴퓨터 안에서 맞붙인다 */
class SyncHttpTest {
    private val dir: File = Files.createTempDirectory("synchttp").toFile()
    private val phone = TestDevice(dir, "phone", roomSchema = true).apply { ensure() }
    private val pc = TestDevice(dir, "pc", roomSchema = false).apply { ensure() }
    private val devices = ConcurrentHashMap<String, PairedDevice>()
    private var pending: ByteArray? = null
    private val store = object : DeviceStore {
        override fun find(id: String) = devices[id]
        override fun save(device: PairedDevice) { devices[device.id] = device }
    }

    private fun server(edition: String = "catholic") = SyncHttpServer(
        SyncServerCore(pc.db, edition, "abc123", "내 PC", store, pc.photos, { pending }, onPaired = { pending = null }),
        0,
    ).apply { start() }

    private var http: SyncHttpServer? = null

    @AfterTest
    fun cleanup() {
        http?.stop()
        phone.close(); pc.close()
        dir.deleteRecursively()
    }

    private fun code(port: Int, key: ByteArray, edition: String = "catholic") =
        PairingCode("abc123", edition, listOf("10.255.255.1", "127.0.0.1"), port, key, "내 PC")

    @Test
    fun pairSyncAndPhotos() {
        val s = server().also { http = it }
        val key = SyncProtocol.newKey()
        pending = key
        val qr = code(s.boundPort, key)
        val parsed = assertNotNull(PairingCode.parse(qr.encode()))
        assertEquals("내 PC", parsed.name)
        assertContentEquals(key, parsed.key)

        val server = SyncClient.pair(parsed, "phone-1", "갤럭시", "catholic")
        assertEquals("127.0.0.1", server.hosts.first(), "응답한 주소를 먼저 쓴다")
        assertTrue(devices.containsKey("phone-1"))

        // 기록과 사진
        val s1 = phone.addRestaurant("사진 맛집")
        File(phone.photoDir, "p_1_1.jpg").writeBytes(byteArrayOf(1, 2, 3))
        phone.addVisit(s1, 20500, "폰 사진", "p_1_1.jpg")
        val s2 = pc.addRestaurant("PC 맛집")
        File(pc.photoDir, "p_2_2.jpg").writeBytes(byteArrayOf(9, 9))
        pc.addVisit(s2, 20501, "PC 사진", "p_2_2.jpg")
        pc.addTodo("PC 할 일")

        val out = SyncRunner.run(phone.db, phone.photos, server, SyncEngine.ClientState.FRESH) { null }
        assertEquals(phone.dump(), pc.dump())
        assertEquals(1, out.photosSent); assertEquals(1, out.photosReceived)
        assertContentEquals(byteArrayOf(1, 2, 3), File(pc.photoDir, "p_1_1.jpg").readBytes())
        assertContentEquals(byteArrayOf(9, 9), File(phone.photoDir, "p_2_2.jpg").readBytes())
        assertTrue(devices.getValue("phone-1").lastSync > 0)

        val again = SyncRunner.run(phone.db, phone.photos, out.server, out.state) { null }
        assertEquals(0, again.sent); assertEquals(0, again.received); assertEquals(0, again.photosSent + again.photosReceived)
    }

    @Test
    fun rejectsWrongEditionUnpairedAndWrongKey() {
        val s = server().also { http = it }
        // 판본이 다르면 휴대폰이 먼저 막는다
        val e1 = assertFailsWith<SyncException> { SyncClient.pair(code(s.boundPort, SyncProtocol.newKey(), "general"), "p", "폰", "catholic") }
        assertEquals(SyncException.Reason.EDITION, e1.reason)
        // 짝 맺기 화면을 띄우지 않았으면 거절
        pending = null
        val e2 = assertFailsWith<SyncException> { SyncClient.pair(code(s.boundPort, SyncProtocol.newKey()), "p", "폰", "catholic") }
        assertEquals(SyncException.Reason.PAIRING_CLOSED, e2.reason)
        // QR과 다른 키로는 짝을 맺을 수 없다
        pending = SyncProtocol.newKey()
        val e3 = assertFailsWith<SyncException> { SyncClient.pair(code(s.boundPort, SyncProtocol.newKey()), "p", "폰", "catholic") }
        assertEquals(SyncException.Reason.PAIRING_CLOSED, e3.reason)
        // 짝 맺지 않은 기기의 동기화는 거절
        val fake = PairedServer("abc123", "PC", "catholic", listOf("127.0.0.1"), s.boundPort, SyncProtocol.newKey(), "stranger")
        val e4 = assertFailsWith<SyncException> { SyncRunner.run(phone.db, phone.photos, fake, SyncEngine.ClientState.FRESH) { null } }
        assertEquals(SyncException.Reason.UNPAIRED, e4.reason)
        // 시계가 많이 다르면 알려 준다
        val key = SyncProtocol.newKey(); pending = key
        val paired = SyncClient.pair(code(s.boundPort, key), "p2", "폰", "catholic")
        val skewed = SyncClient(paired.deviceId, paired.key, clock = { System.currentTimeMillis() - 3_600_000 })
        val e5 = assertFailsWith<SyncException> { skewed.call("127.0.0.1", s.boundPort, "POST", "/sync", "{}".toByteArray()) }
        assertEquals(SyncException.Reason.CLOCK, e5.reason)
    }

    @Test
    fun findsPcAgainWhenAddressChanges() {
        val s = server().also { http = it }
        val key = SyncProtocol.newKey(); pending = key
        val paired = SyncClient.pair(code(s.boundPort, key), "p3", "폰", "catholic")
        // PC의 주소가 바뀐 것처럼: 저장된 주소는 닿지 않고, 검색으로 새 주소를 찾는다
        val moved = paired.copy(hosts = listOf("10.255.255.1"), port = 1)
        phone.addTodo("주소 바뀜")
        val out = SyncRunner.run(phone.db, phone.photos, moved, SyncEngine.ClientState.FRESH) { "127.0.0.1" to s.boundPort }
        assertEquals("127.0.0.1", out.server.hosts.first())
        assertEquals(s.boundPort, out.server.port)
        assertEquals(1, pc.count("todos"))
    }

    @Test
    fun cryptoRejectsTampering() {
        val key = SyncProtocol.newKey()
        val sealed = SyncProtocol.seal(key, "aad", "비밀 일기".toByteArray())
        assertEquals("비밀 일기", String(SyncProtocol.open(key, "aad", sealed)))
        assertFailsWith<Exception> { SyncProtocol.open(key, "other", sealed) }
        sealed[sealed.size - 1] = (sealed.last() + 1).toByte()
        assertFailsWith<Exception> { SyncProtocol.open(key, "aad", sealed) }
        assertFailsWith<Exception> { SyncProtocol.open(SyncProtocol.newKey(), "aad", SyncProtocol.seal(key, "aad", byteArrayOf(1))) }
        assertTrue(SyncProtocol.validPhotoName("p_1_2.jpg"))
        assertTrue(!SyncProtocol.validPhotoName("../secret") && !SyncProtocol.validPhotoName(".."))
    }
}
