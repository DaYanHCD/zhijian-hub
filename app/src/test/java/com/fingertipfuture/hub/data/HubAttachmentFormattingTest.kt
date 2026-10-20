package com.fingertipfuture.hub.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HubAttachmentFormattingTest {

    @Test
    fun `location attachment keeps a readable position label`() {
        val attachment = HubAttachment(
            kind = HubAttachment.KIND_LOCATION,
            lat = 31.2304,
            lon = 121.4737,
            label = "上海",
        )
        assertEquals(
            "上海 纬度 31.2304，经度 121.4737 · 地图：https://maps.google.com/?q=31.2304,121.4737",
            attachment.positionText(),
        )
    }
}
