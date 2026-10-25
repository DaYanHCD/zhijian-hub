package com.fingertipfuture.hub.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PersistentSnapshotCryptoTest {

    @Test
    fun `key derivation is stable for reinstall and changes with app identity`() {
        val salt = ByteArray(32) { it.toByte() }
        val first = PersistentSnapshotCrypto.deriveKey(
            packageName = "com.fingertipfuture.hub.beta",
            androidId = "ssaid",
            signingSha256 = "cert",
            salt = salt,
        )
        val second = PersistentSnapshotCrypto.deriveKey(
            packageName = "com.fingertipfuture.hub.beta",
            androidId = "ssaid",
            signingSha256 = "cert",
            salt = salt,
        )
        val otherSigning = PersistentSnapshotCrypto.deriveKey(
            packageName = "com.fingertipfuture.hub.beta",
            androidId = "ssaid",
            signingSha256 = "other-cert",
            salt = salt,
        )

        assertArrayEquals(first, second)
        assertFalse(first.contentEquals(otherSigning))
    }
}
