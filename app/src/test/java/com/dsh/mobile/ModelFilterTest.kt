package com.dsh.mobile

import com.dsh.mobile.data.ModelItem
import com.dsh.mobile.data.filterModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 模型搜索：模型一多就得能靠关键词快速定位。 */
class ModelFilterTest {

    private fun item(provider: String, modelId: String) = ModelItem(
        id = "$provider::$modelId",
        name = modelId,
        provider = provider,
        modelId = modelId,
        providerName = provider,
        enabled = true,
        contextWindow = "1M",
        imageInput = true,
    )

    private val models = listOf(
        item("codego", "gpt-5.6-sol"),
        item("codego", "gpt-5.6-terra"),
        item("codego", "gpt-5.5"),
        item("codego", "gpt-6-astra"),
        item("wb2api", "deepseek-v4.1-flash"),
        item("wawa", "claude-sonnet-4-6"),
    )

    @Test
    fun emptyQueryKeepsEverything() {
        assertEquals(models.size, filterModels(models, "").size)
        assertEquals(models.size, filterModels(models, "   ").size)
    }

    @Test
    fun matchesModelName() {
        val hit = filterModels(models, "sol")
        assertEquals(1, hit.size)
        assertEquals("gpt-5.6-sol", hit.first().modelId)
    }

    @Test
    fun matchesProviderName() {
        val hit = filterModels(models, "wawa")
        assertEquals(1, hit.size)
        assertEquals("claude-sonnet-4-6", hit.first().modelId)
    }

    @Test
    fun isCaseInsensitiveAndTrims() {
        assertEquals(1, filterModels(models, "  SOL ").size)      // 大写 + 空格照样命中
        assertEquals(2, filterModels(models, "GPT-5.6").size)      // sol / terra 两个
    }

    @Test
    fun noMatchReturnsEmpty() {
        assertTrue(filterModels(models, "不存在的模型xyz").isEmpty())
    }
}
