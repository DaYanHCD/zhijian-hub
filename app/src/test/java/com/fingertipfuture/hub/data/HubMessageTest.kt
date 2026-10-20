package com.fingertipfuture.hub.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HubMessageTest {

    @Test
    fun `deepseek successful searches contribute to cost`() {
        val message = HubMessage(
            role = "assistant",
            text = "",
            at = 1L,
            searchTraces = listOf(
                HubSearchTrace(
                    query = "OpenAI news",
                    resultCount = 10,
                    providerKinds = listOf(SearchProvider.KIND_DEEPSEEK),
                ),
                HubSearchTrace(
                    query = "failed",
                    providerKinds = listOf(SearchProvider.KIND_DEEPSEEK),
                    error = "HTTP 500",
                ),
                HubSearchTrace(
                    query = "bing",
                    resultCount = 5,
                    providerKinds = listOf(SearchProvider.KIND_BING),
                ),
            ),
        )
        assertEquals(0.005, message.searchCostYuan, 0.0000001)
    }

    @Test
    fun `cached deepseek searches are not billed again`() {
        val message = HubMessage(
            role = "assistant",
            text = "",
            at = 1L,
            searchTraces = listOf(
                HubSearchTrace(
                    query = "OpenAI news",
                    resultCount = 10,
                    providerKinds = listOf(SearchProvider.KIND_DEEPSEEK),
                    cached = true,
                ),
            ),
        )
        assertEquals(0.0, message.searchCostYuan, 0.0000001)
    }
}
