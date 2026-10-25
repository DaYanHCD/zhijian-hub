package com.fingertipfuture.hub.ui.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangelogFormattingTest {

    @Test
    fun `legacy categories normalize to the three public categories`() {
        assertEquals("更新：新增功能", normalizeChangelogLine("新增：新增功能"))
        assertEquals("更新：升级底层", normalizeChangelogLine("升级：升级底层"))
        assertEquals("优化：调整界面", normalizeChangelogLine("调整：调整界面"))
        assertEquals("优化：补充说明", normalizeChangelogLine("说明：补充说明"))
        assertEquals("修复：修復問題", normalizeChangelogLine("修復：修復問題"))
        assertEquals("优化：優化效能", normalizeChangelogLine("優化：優化效能"))
    }

    @Test
    fun `new categories stay unchanged`() {
        assertEquals("更新：新增入口", normalizeChangelogLine("更新：新增入口"))
        assertEquals("优化：减少占用", normalizeChangelogLine("优化：减少占用"))
        assertEquals("修复：修正崩溃", normalizeChangelogLine("修复：修正崩溃"))
    }
}
