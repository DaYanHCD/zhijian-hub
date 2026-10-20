package com.fingertipfuture.hub.data

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttachmentPathPolicyTest {

    @Test
    fun `only app owned attachment and voice files are accepted`() {
        val root = Files.createTempDirectory("hub-path-policy").toFile()
        val externalDir = Files.createTempDirectory("hub-path-external").toFile()
        try {
            val attachments = java.io.File(root, "attachments").apply { mkdirs() }
            val voice = java.io.File(root, "voice").apply { mkdirs() }
            val image = java.io.File(attachments, "a.jpg").apply { writeText("x") }
            val audio = java.io.File(voice, "v.m4a").apply { writeText("x") }
            val outside = java.io.File(externalDir, "a.jpg")
                .apply { writeText("secret") }

            assertEquals(image.absolutePath, AttachmentPathPolicy.ownedPath(root, image.absolutePath))
            assertEquals(audio.absolutePath, AttachmentPathPolicy.ownedPath(root, audio.absolutePath))
            assertNull(AttachmentPathPolicy.ownedPath(root, outside.absolutePath))
            assertEquals(image.absolutePath, AttachmentPathPolicy.remapImported(root, outside.absolutePath))
        } finally {
            root.deleteRecursively()
            externalDir.deleteRecursively()
        }
    }

    @Test
    fun `unknown external path is cleared instead of being trusted`() {
        val root = Files.createTempDirectory("hub-path-policy").toFile()
        val externalDir = Files.createTempDirectory("hub-path-external").toFile()
        try {
            val outside = java.io.File(externalDir, "not-owned-${System.nanoTime()}.txt")
                .apply { writeText("secret") }
            assertNull(AttachmentPathPolicy.remapImported(root, outside.absolutePath))
        } finally {
            root.deleteRecursively()
            externalDir.deleteRecursively()
        }
    }
}
