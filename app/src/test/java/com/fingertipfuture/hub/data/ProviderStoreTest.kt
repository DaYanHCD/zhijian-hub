package com.fingertipfuture.hub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderStoreTest {

    @Test
    fun `new provider uses the openai compatible chat path by default`() {
        val provider = HubProvider(
            id = "custom",
            name = "自定义",
            baseUrl = "https://example.com/v1",
        )

        assertEquals(HubProvider.DEFAULT_API_PATH, provider.apiPath)
        assertEquals("/chat/completions", provider.apiPath)
    }

    @Test
    fun `all built in providers keep the chat completions path`() {
        assertTrue(
            HUB_PRESETS.all { it.apiPath == HubProvider.DEFAULT_API_PATH },
        )
    }

}
