package com.fingertipfuture.hub.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UpdateRepositoryTest {

    @Test
    fun `version name cannot escape the download directory`() {
        val name = UpdateRepository.safeApkFileName("../../evil/1.0.18")
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertEquals("hub-evil_1.0.18.apk", name)
    }

    @Test
    fun `normal version name is preserved`() {
        assertEquals("hub-1.0.18.apk", UpdateRepository.safeApkFileName("1.0.18"))
    }
}
