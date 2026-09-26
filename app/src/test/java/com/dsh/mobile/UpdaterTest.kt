package com.dsh.mobile

import com.dsh.mobile.data.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本号比较：一位小数新格式（0.3）与旧三段格式（0.2.77）混着比也要正确——
 * 0.2.77 的存量用户升级到 0.3 全靠这里的逐段比较。
 */
class UpdaterTest {

    @Test
    fun oneDecimalVersionsCompareComponentWise() {
        assertTrue("0.3 应比 0.2.77 新（跨格式）", Updater.isNewer("0.3", "0.2.77"))
        assertTrue(Updater.isNewer("0.4", "0.3"))
        assertTrue(Updater.isNewer("0.10", "0.9")) // 数字比较，不是字符串
        assertTrue(Updater.isNewer("v0.3", "0.2.77")) // 容忍 v 前缀
        assertFalse("反向不成立", Updater.isNewer("0.2.77", "0.3"))
        assertFalse("等价不算更新", Updater.isNewer("0.3", "0.3.0"))
        assertFalse(Updater.isNewer("0.2.77", "0.2.77"))
    }

    @Test
    fun versionPartsParsesCommonShapes() {
        assertEquals(listOf(0, 3), Updater.versionParts("0.3"))
        assertEquals(listOf(0, 2, 77), Updater.versionParts("v0.2.77"))
        assertEquals(listOf(0, 6), Updater.versionParts("v0.6"))
        // 非数字段（rc 之类）被跳过，后面的数字继续收集
        assertEquals(listOf(1, 2, 3, 4), Updater.versionParts("1.2.3-rc.4"))
        assertTrue(Updater.versionParts("").isEmpty())
    }
}
