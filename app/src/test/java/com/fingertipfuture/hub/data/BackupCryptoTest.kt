package com.fingertipfuture.hub.data

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {

    private val settings = BackupStore.WebDav(
        id = "dav-test",
        url = "https://dav.example.com/remote.php/dav/files/user",
        user = "user",
        password = "test-password",
        path = "hub/backup",
    )

    @Test
    fun `bytes round trip is encrypted and decryptable`() {
        val plain = "聊天记录".repeat(100).toByteArray(Charsets.UTF_8)
        val encrypted = BackupCrypto.encryptBytes(plain, settings)
        assertFalse(plain.contentEquals(encrypted))
        assertTrue(BackupCrypto.hasMagic(encrypted))
        assertArrayEquals(plain, BackupCrypto.decryptBytes(encrypted, settings))
    }

    @Test
    fun `legacy plaintext is passed through unchanged`() {
        val plain = "legacy".toByteArray(Charsets.UTF_8)
        assertArrayEquals(plain, BackupCrypto.decryptBytes(plain, settings))
    }

    @Test
    fun `zip file round trip keeps bytes`() {
        val source = File.createTempFile("hub-crypto-src", ".bin")
        val encrypted = File.createTempFile("hub-crypto-enc", ".bin")
        val restored = File.createTempFile("hub-crypto-out", ".bin")
        try {
            source.writeBytes(ByteArray(4096) { (it % 251).toByte() })
            BackupCrypto.encryptFileTo(source, encrypted, settings)
            BackupCrypto.decryptFileTo(encrypted, restored, settings)
            assertArrayEquals(source.readBytes(), restored.readBytes())
        } finally {
            source.delete()
            encrypted.delete()
            restored.delete()
        }
    }
}
