package com.fingertipfuture.hub.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalChangelogTest {

    @Test
    fun keepsAllPreviousVersionsAndPutsCurrentFirst() {
        val older = VersionInfo(61L, "1.0.53", changelog = listOf("旧日志"))
        val previous = VersionInfo(62L, "1.0.54", changelog = listOf("上一版"))
        val current = VersionInfo(
            versionCode = 63L,
            versionName = "1.0.55",
            changelog = listOf(LocalChangelog.DEFAULT_CHANGELOG),
        )

        val manifest = assembleLocalManifest(
            currentCode = 63L,
            currentName = "1.0.55",
            bundledEntries = listOf(previous, older),
            authored = mapOf(63L to current),
        )

        assertEquals(current, manifest.latest)
        assertEquals(listOf(62L, 61L), manifest.history.map { it.versionCode })
    }

    @Test
    fun fallsBackToDefaultTextWhenCurrentHasNoAuthoredEntry() {
        val manifest = assembleLocalManifest(
            currentCode = 64L,
            currentName = "1.0.56",
            bundledEntries = listOf(VersionInfo(63L, "1.0.55")),
            authored = emptyMap(),
        )

        assertEquals(LocalChangelog.DEFAULT_CHANGELOG, manifest.latest.changelog.single())
        assertEquals(63L, manifest.history.single().versionCode)
    }

    @Test
    fun bundledHistoryDoesNotRequireDownloadUrlsOrChecksums() {
        val entries = parseBundledHistory(
            """
            {
              "latest": {
                "versionCode": 62,
                "versionName": "1.0.54",
                "releasedAt": "2026-10-24",
                "changelog": ["上一版"]
              },
              "history": [
                {
                  "versionCode": 61,
                  "versionName": "1.0.53",
                  "releasedAt": "2026-10-23",
                  "changelog": ["更早版本"]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf(62L, 61L), entries.map { it.versionCode })
        assertEquals(listOf("上一版"), entries.first().changelog)
    }

    @Test
    fun bundledCurrentVersionProvidesItsChangelog() {
        val current = VersionInfo(
            versionCode = 2L,
            versionName = "1.0.1",
            releasedAt = "2026-10-25",
            changelog = listOf("开源版更新链路"),
        )

        val manifest = assembleLocalManifest(
            currentCode = 2L,
            currentName = "1.0.1",
            bundledEntries = listOf(current, VersionInfo(1L, "1.0.0")),
            authored = emptyMap(),
        )

        assertEquals(current, manifest.latest)
        assertEquals(listOf(1L), manifest.history.map { it.versionCode })
    }

    @Test
    fun openSourceManifestNeverMergesBetaAuthoredHistory() {
        assertTrue(
            selectAuthoredReleases(
                openSourceBuild = true,
                betaReleases = mapOf(65L to VersionInfo(65L, "1.0.57")),
            ).isEmpty(),
        )
        assertEquals(
            setOf(65L),
            selectAuthoredReleases(
                openSourceBuild = false,
                betaReleases = mapOf(65L to VersionInfo(65L, "1.0.57")),
            ).keys,
        )
    }
}
