package com.fingertipfuture.hub.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateApiTest {

    @Test
    fun `parses latest update and history`() {
        val manifest = UpdateApi.parseManifest(
            """
            {
              "generatedAt": "2026-10-24T23:00:00+08:00",
              "latest": {
                "versionCode": 65,
                "versionName": "1.0.57",
                "apkUrl": "https://fingertipfuture.com/hub/update/apk/hub-1.0.57-beta.apk",
                "apkUrlHttps": "https://fingertipfuture.com/hub/update/apk/hub-1.0.57-beta.apk",
                "apkSize": 123456,
                "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "releasedAt": "2026-10-25",
                "changelog": ["恢复联网更新"],
                "forceUpdate": false
              },
              "history": [
                {
                  "versionCode": 64,
                  "versionName": "1.0.56",
                  "releasedAt": "2026-10-24",
                  "changelog": ["上一版"]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(65L, manifest.latest.versionCode)
        assertEquals("1.0.57", manifest.latest.versionName)
        assertEquals(123456L, manifest.latest.apkSize)
        assertEquals(listOf(64L), manifest.history.map { it.versionCode })
        assertTrue(manifest.latest.apkUrlHttps!!.startsWith("https://"))
    }

    @Test
    fun `parses independent open source channel metadata`() {
        val manifest = UpdateApi.parseManifest(
            """
            {
              "schemaVersion": 1,
              "channel": "os",
              "packageName": "com.fingertipfuture.hub.os",
              "latest": {
                "versionCode": 3,
                "versionName": "1.0.2",
                "apkUrl": "https://fingertipfuture.com/hub/os/update/apk/hub-1.0.2-os.apk",
                "apkSize": 100,
                "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "changelog": ["开源版更新"]
              },
              "history": []
            }
            """.trimIndent(),
        )

        assertEquals(1, manifest.schemaVersion)
        assertEquals("os", manifest.channel)
        assertEquals("com.fingertipfuture.hub.os", manifest.packageName)
        requireMatchingUpdateChannel(
            manifest,
            expectedChannel = "os",
            expectedPackage = "com.fingertipfuture.hub.os",
            requireExplicit = true,
        )
    }

    @Test
    fun `rejects beta or wrong package manifest on open channel`() {
        val manifest = UpdateApi.parseManifest(
            """
            {
              "schemaVersion": 1,
              "channel": "beta",
              "packageName": "com.fingertipfuture.hub.beta",
              "latest": {
                "versionCode": 69,
                "versionName": "1.0.61",
                "apkUrl": "https://fingertipfuture.com/hub/update/apk/hub-1.0.61-beta.apk",
                "apkSize": 100,
                "sha256": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
              }
            }
            """.trimIndent(),
        )

        assertThrows(UpdateChannelMismatchException::class.java) {
            requireMatchingUpdateChannel(
                manifest,
                expectedChannel = "os",
                expectedPackage = "com.fingertipfuture.hub.os",
                requireExplicit = true,
            )
        }
    }
}
