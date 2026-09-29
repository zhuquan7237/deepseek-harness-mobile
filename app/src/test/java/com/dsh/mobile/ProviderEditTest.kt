package com.dsh.mobile

import com.dsh.mobile.data.ModelItem
import com.dsh.mobile.data.apiModeLabel
import com.dsh.mobile.data.applyProviderEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「编辑提供商」的两条纯函数契约：
 *  - [applyProviderEdit]：改动必须整组落到该提供商的每个模型行上（提供商级字段
 *    在共享文档里是行级冗余，两端 diff 都读行上的值）；
 *  - [apiModeLabel]：三个已知协议值给简写，其它值原样透传（不许吞掉未知值）。
 */
class ProviderEditTest {

    private fun item(
        provider: String,
        model: String,
        providerName: String = "旧名",
        baseURL: String = "https://old.example/v1",
        apiMode: String = "openai-completions",
        keyRef: String = "OLD_KEY",
    ) = ModelItem(
        id = "$provider::$model",
        name = model,
        provider = provider,
        modelId = model,
        enabled = true,
        contextWindow = "—",
        imageInput = false,
        providerName = providerName,
        order = 0,
        baseURL = baseURL,
        apiMode = apiMode,
        apiKeyRef = keyRef,
        paramsJson = "{\"contextWindow\":128000}",
        tagsJson = "[]",
    )

    @Test
    fun `edit spreads to every model of the provider`() {
        val items = listOf(item("p1", "m1"), item("p1", "m2"))
        val out = applyProviderEdit(items, "p1", "新名", "https://new.example/v1", "openai-responses", "NEW_KEY")
        assertEquals(2, out.size)
        out.forEach {
            assertEquals("新名", it.providerName)
            assertEquals("https://new.example/v1", it.baseURL)
            assertEquals("openai-responses", it.apiMode)
            assertEquals("NEW_KEY", it.apiKeyRef)
        }
    }

    @Test
    fun `other providers untouched`() {
        val items = listOf(
            item("p1", "m1"),
            item("p2", "x1", baseURL = "https://keep.example/v1", apiMode = "openai-responses"),
        )
        val out = applyProviderEdit(items, "p1", "新名", "https://new.example/v1", "openai-responses", "NEW_KEY")
        val other = out.first { it.provider == "p2" }
        assertEquals("https://keep.example/v1", other.baseURL)
        assertEquals("openai-responses", other.apiMode)
        assertEquals("OLD_KEY", other.apiKeyRef)
    }

    @Test
    fun `identity and params preserved`() {
        val items = listOf(item("p1", "m1"))
        val out = applyProviderEdit(items, "p1", "新名", "https://new.example/v1", "openai-responses", "NEW_KEY")
        val row = out.single()
        assertEquals("p1::m1", row.id)
        assertEquals("m1", row.modelId)
        assertEquals("{\"contextWindow\":128000}", row.paramsJson)
        assertTrue(row.enabled)
    }

    @Test
    fun `no rows for the provider returns the list unchanged`() {
        val items = listOf(item("p2", "x1"))
        val out = applyProviderEdit(items, "p1", "新名", "https://new.example/v1", "openai-responses", "NEW_KEY")
        assertEquals(items, out)
    }

    @Test
    fun `apiModeLabel maps known modes and passes unknown through`() {
        assertEquals("Responses", apiModeLabel("openai-responses"))
        assertEquals("Chat Completions", apiModeLabel("openai-completions"))
        assertEquals("Anthropic", apiModeLabel("anthropic-messages"))
        assertEquals("", apiModeLabel(""))
        assertEquals("azure-openai-responses", apiModeLabel("azure-openai-responses"))
    }
}
