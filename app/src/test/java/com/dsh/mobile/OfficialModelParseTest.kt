package com.dsh.mobile

import com.dsh.mobile.data.Wire
import com.dsh.mobile.data.effortLabel
import com.dsh.mobile.data.filterModels
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 官方提供商的线上形状：桥接 0.2.45 起 `/mobile/models` 会把手写提供商之外的
 * 引擎路由（官方登录 `deepseek-account`、官方 API key `deepseek-official`）
 * 一起返回，并带 `readonly` 与真实的 params（上下文 / 模态 / 思考档位 / 默认档位）。
 *
 * 这些字段决定手机端两件事：能不能选中它（要出现在 items 里）、能不能改它
 * （readonly 必须解析出来，否则模型页会给出编辑/删除入口）。
 */
class OfficialModelParseTest {

    private fun sample(): JSONObject = JSONObject(
        """
        {
          "revision": 7,
          "overlayRevision": 3,
          "providers": [
            { "id": "codego", "name": "codego", "apiKeyConfigured": true },
            { "id": "deepseek-account", "name": "DeepSeek Account", "apiKeyConfigured": true, "readonly": true }
          ],
          "items": [
            {
              "id": "codego::gpt-5.6-sol", "provider": "codego", "modelId": "gpt-5.6-sol",
              "enabled": true, "tags": [], "order": 0, "params": { "contextWindow": 1050000 }
            },
            {
              "id": "deepseek-account::deepseek-flash", "provider": "deepseek-account",
              "providerName": "DeepSeek Account", "apiKeyConfigured": true,
              "modelId": "deepseek-flash", "name": "DeepSeek-V4.1-Flash",
              "params": {
                "contextWindow": 1048576,
                "maxTokens": 256000,
                "input": ["text", "image"],
                "reasoningEfforts": { "off": "off", "low": "low", "high": "high" },
                "defaultReasoningEffort": "high"
              },
              "enabled": true, "tags": [], "order": 0, "readonly": true
            }
          ]
        }
        """.trimIndent(),
    )

    @Test
    fun parsesReadonlyProvidersAndModels() {
        val doc = Wire.parseModelDoc(sample())

        assertEquals(2, doc.providers.size)
        val official = doc.providers.first { it.id == "deepseek-account" }
        assertTrue("官方 provider 必须标只读", official.readonly)
        assertFalse(doc.providers.first { it.id == "codego" }.readonly)

        assertEquals(2, doc.items.size)
        val flash = doc.items.first { it.modelId == "deepseek-flash" }
        assertTrue("官方模型必须标只读", flash.readonly)
        assertEquals("DeepSeek Account", flash.providerName)
        assertEquals("DeepSeek-V4.1-Flash", flash.name)
        assertEquals("1048576", flash.contextWindow)
        assertTrue("图片能力来自 params.input", flash.imageInput)
        assertEquals(listOf("off", "low", "high").sorted(), flash.efforts.sorted())
        assertEquals("high", flash.defaultEffort)
    }

    @Test
    fun officialModelsAreSearchableAndLabelled() {
        val doc = Wire.parseModelDoc(sample())
        val hits = filterModels(doc.items, "deepseek")
        assertEquals(listOf("deepseek-flash"), hits.map { it.modelId })

        assertEquals("关", effortLabel("off"))
        assertEquals("关", effortLabel("none"))
        assertEquals("高", effortLabel("high"))
    }
}
