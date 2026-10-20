package com.fingertipfuture.hub.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatRecommendationDeckTest {

    @Test
    fun noProvider_startsWithProviderGuide() {
        val cards = buildChatRecommendations(
            hasProvider = false,
            hasSearchProvider = false,
            hasAnyPermission = false,
        )

        assertEquals(3, cards.size)
        assertEquals(
            listOf(
                ChatRecommendationKind.Provider,
                ChatRecommendationKind.Search,
                ChatRecommendationKind.Permissions,
            ),
            cards.map { it.kind },
        )
        assertEquals(listOf(1, 2, 3), cards.map { it.number })
    }

    @Test
    fun providerWithoutSearch_startsWithSearchGuide() {
        val cards = buildChatRecommendations(
            hasProvider = true,
            hasSearchProvider = false,
            hasAnyPermission = false,
        )

        assertEquals(
            listOf(
                ChatRecommendationKind.Search,
                ChatRecommendationKind.Permissions,
                ChatRecommendationKind.WeatherPrompt,
            ),
            cards.map { it.kind },
        )
    }

    @Test
    fun providerAndSearchWithoutPermissions_startsWithPermissionGuide() {
        val cards = buildChatRecommendations(
            hasProvider = true,
            hasSearchProvider = true,
            hasAnyPermission = false,
        )

        assertEquals(
            listOf(
                ChatRecommendationKind.Permissions,
                ChatRecommendationKind.WeatherPrompt,
                ChatRecommendationKind.UsagePrompt,
            ),
            cards.map { it.kind },
        )
    }

    @Test
    fun allConfigured_showsThreePromptCards() {
        val cards = buildChatRecommendations(
            hasProvider = true,
            hasSearchProvider = true,
            hasAnyPermission = true,
        )

        assertEquals(
            listOf(
                ChatRecommendationKind.WeatherPrompt,
                ChatRecommendationKind.UsagePrompt,
                ChatRecommendationKind.VirusPrompt,
            ),
            cards.map { it.kind },
        )
        assertEquals(
            listOf("现在天气怎么样", "看看今天用了多久的设备", "手机里有没有病毒软件呢？"),
            cards.map { it.title },
        )
    }
}
