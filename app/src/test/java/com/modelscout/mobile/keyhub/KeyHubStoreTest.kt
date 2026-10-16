package com.modelscout.mobile.keyhub

import com.modelscout.mobile.keyhub.checker.Endpoint
import com.modelscout.mobile.keyhub.checker.KeyCheckEngine
import com.modelscout.mobile.keyhub.checker.KeyChecker
import com.modelscout.mobile.keyhub.detect.ExtractedKey
import com.modelscout.mobile.keyhub.model.Confidence
import com.modelscout.mobile.keyhub.model.KeyStatus
import com.modelscout.mobile.keyhub.model.Platform
import com.modelscout.mobile.keyhub.storage.KeyVault
import com.modelscout.mobile.keyhub.storage.XorCipher
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 「本地保存」开关的端到端行为（验收清单：开关开数据在且文件不可读 / 开关关退出清空）。 */
class KeyHubStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(200).setBody(BALANCE_BODY)
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun store(persist: Boolean): KeyHubStore {
        val checker = KeyChecker(KeyHubHttp.create()) { spec, record ->
            Endpoint(server.url("/" + record.id).toString(), isBalance = spec.hasBalanceEndpoint)
        }
        return KeyHubStore(
            vault = KeyVault(temp.root, XorCipher()),
            engine = KeyCheckEngine(checker),
        ).apply { persistEnabled = persist }
    }

    private fun key(raw: String, platform: Platform) =
        listOf(ExtractedKey(raw, null, platform, Confidence.CERTAIN))

    private val keysFile get() = File(temp.root, KeyHubConfig.FILE_KEYS)

    @Test
    fun `persist off keeps data in memory only and loses it on restart`() {
        val store = store(persist = false)
        store.add(key("sk-proj-AAAAAAAAAAAAAAAAAAAAAAAAAAAA", Platform.OPENAI))

        assertEquals(1, store.records.value.size)
        assertFalse("开关关闭时不该写盘", keysFile.exists())

        val restarted = store(persist = false)
        restarted.restore()
        assertTrue("开关关闭，重启后应当为空", restarted.records.value.isEmpty())
    }

    @Test
    fun `persist on survives a restart and the file is not readable plaintext`() {
        val store = store(persist = true)
        store.add(key("sk-proj-BBBBBBBBBBBBBBBBBBBBBBBBBBBB", Platform.OPENAI))

        assertTrue(keysFile.exists())
        val onDisk = keysFile.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse("文件里不该有明文 Key", onDisk.contains("sk-proj-BBBBBBBBBBBBBBBBBBBBBBBBBBBB"))

        val restarted = store(persist = true)
        restarted.restore()
        assertEquals(1, restarted.records.value.size)
        assertEquals("sk-proj-BBBBBBBBBBBBBBBBBBBBBBBBBBBB", restarted.records.value[0].rawKey)
    }

    @Test
    fun `check result and history are persisted`() = runBlocking {
        val store = store(persist = true)
        store.add(key("sk-fake0000000000000000000000000000", Platform.DEEPSEEK))
        val id = store.records.value[0].id

        val updated = store.checkOne(id)!!
        assertEquals(KeyStatus.AVAILABLE, updated.lastStatus)
        assertEquals(10.0, updated.lastBalance!!, 1e-9)
        assertEquals("CNY", updated.lastCurrency)
        assertEquals(1, store.historyFor(store.records.value[0]).size)

        val restarted = store(persist = true)
        restarted.restore()
        assertEquals(1, restarted.history.value.size)
        assertEquals(KeyStatus.AVAILABLE, restarted.history.value[0].status)
        // 历史里只有指纹，没有明文 Key
        assertEquals(16, restarted.history.value[0].fp.length)
        assertFalse(restarted.history.value[0].fp.contains("sk-"))
    }

    @Test
    fun `turning the switch off deletes files and clearAll wipes memory`() {
        val store = store(persist = true)
        store.add(key("sk-proj-CCCCCCCCCCCCCCCCCCCCCCCCCCCC", Platform.OPENAI))
        assertTrue(keysFile.exists())

        store.onPersistDisabled()
        assertFalse("关闭开关应删除文件", keysFile.exists())

        store.clearAll()
        assertTrue(store.records.value.isEmpty())
        assertTrue(store.history.value.isEmpty())
    }

    @Test
    fun `adding the same key twice skips the duplicate`() {
        val store = store(persist = false)
        val raw = "sk-proj-DDDDDDDDDDDDDDDDDDDDDDDDDDDD"
        store.add(key(raw, Platform.OPENAI))
        val second = store.add(key(raw, Platform.OPENAI))

        assertEquals(0, second.added)
        assertEquals(1, second.skippedExisting)
        assertEquals(1, store.records.value.size)
    }

    @Test
    fun `storage only platform stays pending and never hits the network`() = runBlocking {
        val store = store(persist = false)
        store.add(key("a1b2c3d4e5f6g7h8.0123456789abcdef", Platform.ZHIPU))
        val before = server.requestCount

        val updated = store.checkOne(store.records.value[0].id)!!

        assertEquals(KeyStatus.PENDING, updated.lastStatus)
        assertEquals("仅存储平台不该发请求", before, server.requestCount)
    }

    // ---------------------------------------------------------------- 批量编辑

    @Test
    fun `manual add marks every key as platform overridden`() {
        val store = store(persist = false)
        val batch = listOf(
            ExtractedKey("sk-00000000000000000000000000000001", null, Platform.OPENAI, Confidence.INFERRED),
            ExtractedKey("sk-00000000000000000000000000000002", null, Platform.OPENAI, Confidence.INFERRED),
        )
        // 「手动指定」模式：入库时整批换成 DeepSeek，并打上手动标记
        val manual = batch.map { it.copy(platform = Platform.DEEPSEEK) }
        store.add(manual, overridden = true)

        assertEquals(2, store.records.value.size)
        store.records.value.forEach { record ->
            assertEquals(Platform.DEEPSEEK, record.platform)
            assertTrue("手动指定必须留痕，否则重测会被自动识别覆盖", record.platformOverridden)
        }
    }

    @Test
    fun `auto add leaves the override flag off`() {
        val store = store(persist = false)
        store.add(key("sk-proj-EEEEEEEEEEEEEEEEEEEEEEEEEEEE", Platform.OPENAI))

        assertFalse(store.records.value[0].platformOverridden)
    }

    @Test
    fun `bulk platform change rewrites platform and voids old conclusions`() = runBlocking {
        val store = store(persist = true)
        val batch = (1..3).map {
            ExtractedKey("sk-0000000000000000000000000000000$it", null, Platform.OPENAI, Confidence.INFERRED)
        }
        store.add(batch)
        val ids = store.records.value.map { it.id }

        // 先跑一轮检测，制造出「已有结论」的状态
        store.checkAll(concurrency = 1)
        assertTrue(store.records.value.all { it.lastStatus == KeyStatus.AVAILABLE })

        val changed = store.updatePlatformBulk(ids.take(2), Platform.DEEPSEEK)

        assertEquals(2, changed)
        store.records.value.take(2).forEach { record ->
            assertEquals(Platform.DEEPSEEK, record.platform)
            assertTrue(record.platformOverridden)
            // 换平台后旧结论必须作废，否则界面会拿 OpenAI 的余额冒充 DeepSeek 的
            assertEquals(KeyStatus.PENDING, record.lastStatus)
            assertEquals(null, record.lastBalance)
            assertEquals(null, record.lastCheckedAt)
        }
        // 没被选中的那条保持原样
        assertEquals(Platform.OPENAI, store.records.value[2].platform)
        assertEquals(KeyStatus.AVAILABLE, store.records.value[2].lastStatus)
    }

    @Test
    fun `bulk label change sets and clears labels`() {
        val store = store(persist = false)
        val batch = (1..2).map {
            ExtractedKey("sk-0000000000000000000000000000000$it", null, Platform.OPENAI, Confidence.INFERRED)
        }
        store.add(batch)
        val ids = store.records.value.map { it.id }

        assertEquals(2, store.updateLabelBulk(ids, "生产"))
        assertTrue(store.records.value.all { it.label == "生产" })

        // 空白 = 清除标签
        assertEquals(2, store.updateLabelBulk(ids, "   "))
        assertTrue(store.records.value.all { it.label == null })
    }

    @Test
    fun `bulk delete removes only the selected ids and ignores unknown ones`() {
        val store = store(persist = false)
        val batch = (1..3).map {
            ExtractedKey("sk-0000000000000000000000000000000$it", null, Platform.OPENAI, Confidence.INFERRED)
        }
        store.add(batch)
        val ids = store.records.value.map { it.id }

        assertEquals(2, store.removeBulk(listOf(ids[0], ids[2], "does-not-exist")))
        assertEquals(listOf(ids[1]), store.records.value.map { it.id })
        assertEquals(0, store.removeBulk(emptyList()))
    }

    private companion object {
        const val BALANCE_BODY =
            """{"is_available":true,"balance_infos":[{"currency":"CNY","total_balance":"10.00"}]}"""
    }
}
