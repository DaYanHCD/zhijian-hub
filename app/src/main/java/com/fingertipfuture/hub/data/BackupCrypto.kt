package com.fingertipfuture.hub.data

import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * WebDAV 备份内容的 AES/GCM 加密。
 *
 * 不依赖本机 Keystore：备份要能在换机后用同一台 WebDAV 的账号密码恢复，
 * 所以密钥由「WebDAV 地址 + 用户名 + 密码」经 PBKDF2 派生。旧版明文备份
 * 没有 HUB1 头，读取时原样返回，保证向后兼容。
 */
internal object BackupCrypto {

    private val MAGIC = byteArrayOf('H'.code.toByte(), 'U'.code.toByte(), 'B'.code.toByte(), '1'.code.toByte())
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val IV_BYTES = 12

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.size + IV_BYTES && MAGIC.indices.all { bytes[it] == MAGIC[it] }

    fun encryptBytes(data: ByteArray, settings: BackupStore.WebDav): ByteArray {
        val cipher = cipher(Cipher.ENCRYPT_MODE, settings)
        val iv = cipher.iv
        return MAGIC + iv + cipher.doFinal(data)
    }

    fun decryptBytes(data: ByteArray, settings: BackupStore.WebDav): ByteArray {
        if (!isEncrypted(data)) return data
        val iv = data.copyOfRange(MAGIC.size, MAGIC.size + IV_BYTES)
        val cipher = cipher(Cipher.DECRYPT_MODE, settings, iv)
        return cipher.doFinal(data.copyOfRange(MAGIC.size + IV_BYTES, data.size))
    }

    fun encryptFileTo(source: File, dest: File, settings: BackupStore.WebDav) {
        val cipher = cipher(Cipher.ENCRYPT_MODE, settings)
        dest.parentFile?.mkdirs()
        dest.outputStream().buffered().use { out ->
            out.write(MAGIC)
            out.write(cipher.iv)
            CipherOutputStream(out, cipher).use { encrypted ->
                source.inputStream().buffered().use { it.copyTo(encrypted) }
            }
        }
    }

    fun decryptFileTo(source: File, dest: File, settings: BackupStore.WebDav) {
        source.inputStream().buffered().use { input ->
            val magic = ByteArray(MAGIC.size)
            if (input.read(magic) != MAGIC.size || !MAGIC.indices.all { magic[it] == MAGIC[it] }) {
                // 旧版明文备份：直接复制
                source.copyTo(dest, overwrite = true)
                return
            }
            val iv = ByteArray(IV_BYTES)
            if (input.read(iv) != IV_BYTES) throw java.io.IOException("备份文件头损坏")
            dest.parentFile?.mkdirs()
            CipherInputStream(input, cipher(Cipher.DECRYPT_MODE, settings, iv)).use { decrypted ->
                dest.outputStream().buffered().use { decrypted.copyTo(it) }
            }
        }
    }

    private fun cipher(
        mode: Int,
        settings: BackupStore.WebDav,
        iv: ByteArray? = null,
    ): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        val key = SecretKeySpec(deriveKey(settings), "AES")
        if (mode == Cipher.ENCRYPT_MODE) {
            init(mode, key)
        } else {
            init(mode, key, GCMParameterSpec(TAG_BITS, iv ?: throw IllegalArgumentException("缺少 IV")))
        }
    }

    private fun deriveKey(settings: BackupStore.WebDav): ByteArray {
        // 匿名 WebDAV 没有密码时用地址兜底；有密码时地址/用户名/密码共同参与。
        val secret = listOf(settings.url, settings.user, settings.password)
            .joinToString("\u0000")
            .ifBlank { settings.url }
        val salt = MessageDigest.getInstance("SHA-256")
            .digest(("zhijianhub-backup-v1|" + settings.url).toByteArray(Charsets.UTF_8))
        val spec = PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = runCatching {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        }.getOrElse {
            // API 24/25 不一定有 SHA-256 的 PBKDF2，退回 SHA-1（仍是加盐多轮派生）。
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        }
        return factory.generateSecret(spec).encoded
    }

    /** 给测试/调试用：判断一段数据是否是本格式。 */
    fun hasMagic(bytes: ByteArray): Boolean = isEncrypted(bytes)
}
