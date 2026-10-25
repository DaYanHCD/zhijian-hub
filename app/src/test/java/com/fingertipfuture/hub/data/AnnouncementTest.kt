package com.fingertipfuture.hub.data

import com.fingertipfuture.hub.update.HubAnnouncement
import com.fingertipfuture.hub.update.parseAnnouncement
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementTest {

    private val now = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2026-10-18")!!.time

    @Test
    fun `active announcement is visible until expiry`() {
        val announcement = HubAnnouncement(
            id = "a1",
            active = true,
            title = "维护通知",
            message = "今晚维护",
            expiresAt = "2026-10-18",
        )
        assertTrue(announcement.isCurrentlyVisible(now))
        assertFalse(announcement.copy(expiresAt = "2026-10-17").isCurrentlyVisible(now))
    }

    @Test
    fun `inactive or incomplete announcement is hidden`() {
        val base = HubAnnouncement(
            id = "a1",
            active = true,
            title = "维护通知",
            message = "今晚维护",
        )
        assertFalse(base.copy(active = false).isCurrentlyVisible(now))
        assertFalse(base.copy(title = "").isCurrentlyVisible(now))
        assertFalse(base.copy(id = "").isCurrentlyVisible(now))
    }

    @Test
    fun `server announcement json is parsed`() {
        val announcement = parseAnnouncement(
            """
            {
              "id": "2026-10-25-001",
              "active": true,
              "title": "维护通知",
              "message": "今晚维护",
              "level": "warning",
              "url": "https://fingertipfuture.com/",
              "publishedAt": "2026-10-25",
              "expiresAt": "2026-10-26"
            }
            """.trimIndent(),
        )!!

        assertTrue(announcement.isCurrentlyVisible(now))
        assertTrue(announcement.active)
        assertTrue(announcement.title == "维护通知")
        assertTrue(announcement.level == "warning")
    }
}
