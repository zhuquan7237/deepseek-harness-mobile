package com.dsh.mobile

import com.dsh.mobile.data.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本号比较。现行格式「三段一位数」：0.3.0、0.3.1 … 0.3.9，补丁到 9 进位 0.4.0；
 * 存量用户是两位补丁的旧格式（0.2.77），跨格式升级全靠这里的逐段数字比较。
 */
class UpdaterTest {

    @Test
    fun threePartSingleDigitVersionsCompareComponentWise() {
        assertTrue("新格式应比旧格式新（跨格式）", Updater.isNewer("0.3.0", "0.2.77"))
        assertTrue(Updater.isNewer("0.3.1", "0.3.0"))
        assertTrue(Updater.isNewer("0.3.9", "0.3.8"))
        assertTrue("补丁到 9 进位", Updater.isNewer("0.4.0", "0.3.9"))
        assertTrue(Updater.isNewer("0.6.0", "0.5.32"))
        assertTrue(Updater.isNewer("v0.3.0", "0.2.77")) // 容忍 v 前缀
        assertFalse("反向不成立", Updater.isNewer("0.2.77", "0.3.0"))
        assertFalse("等价不算更新", Updater.isNewer("0.3.0", "0.3.0.0"))
        assertFalse(Updater.isNewer("0.3.0", "0.3.0"))
    }

    @Test
    fun versionPartsParsesCommonShapes() {
        assertEquals(listOf(0, 3, 0), Updater.versionParts("0.3.0"))
        assertEquals(listOf(0, 2, 77), Updater.versionParts("v0.2.77"))
        // 非数字段（rc 之类）被跳过，后面的数字继续收集
        assertEquals(listOf(1, 2, 3, 4), Updater.versionParts("1.2.3-rc.4"))
        assertTrue(Updater.versionParts("").isEmpty())
    }
}
