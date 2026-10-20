package com.fingertipfuture.hub.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 本机密钥加密（Android Keystore + AES/GCM）。
 *
 * SharedPreferences 只存密文；Keystore 里的主密钥不可导出。兼容改造前已经落盘的明文：
 * [decrypt] 遇到没有前缀的旧值会原样返回，下一次保存时自动写成密文。
 */
internal object SecretStore {

    private const val ALIAS = "hub_secret_v1"
    private const val PREFIX = "enc:v1:"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /**
     * 最近一轮解密是否失败。
     *
     * 调用方在 initialize 时先 reset，再读取多个 Key；只要有一次失败就不应该回写，
     * 否则会把失败的密文用空字符串覆盖掉，造成不可逆的 Key 丢失。
     */
    @Volatile
    var lastDecryptFailed: Boolean = false
        private set

    fun resetDecryptFailure() {
        lastDecryptFailed = false
    }

    fun encrypt(value: String): String {
        if (value.isBlank() || value.startsWith(PREFIX)) return value
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key())
            }
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val packed = cipher.iv + encrypted
            PREFIX + Base64.encodeToString(packed, Base64.NO_WRAP)
        } catch (t: Throwable) {
            // 不能静默退回明文：宁可这次保存失败，也不能让用户误以为 Key 已加密。
            throw IllegalStateException("无法安全加密密钥，请检查系统 Keystore 后重试", t)
        }
    }

    fun decrypt(value: String): String {
        if (value.isBlank() || !value.startsWith(PREFIX)) return value
        return runCatching {
            val packed = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
            require(packed.size > IV_BYTES)
            val iv = packed.copyOfRange(0, IV_BYTES)
            val encrypted = packed.copyOfRange(IV_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            }
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.onFailure { lastDecryptFailed = true }.getOrDefault("")
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
        }.generateKey()
    }
}
