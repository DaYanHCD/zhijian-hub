package com.fingertipfuture.hub.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.fingertipfuture.hub.core.StorageLocations
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 跨卸载快照加密。
 *
 * Keystore 会随卸载删除，因此这里使用应用签名、包名、App 专属 SSAID 和公开盐派生密钥。
 * Android 8+ 的 SSAID 按签名密钥隔离，同签名的覆盖安装和卸载重装保持不变；换签名后旧快照
 * 无法解密，这是有意的安全边界。
 */
internal object PersistentSnapshotCrypto {
    private const val TAG = "HubPersistenceCrypto"
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val PREFIX = "HUBP2"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun encrypt(context: Context, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(ALGORITHM).apply {
            init(Cipher.ENCRYPT_MODE, key(context))
        }
        return MAGIC + cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(context: Context, packed: ByteArray): ByteArray {
        if (!hasMagic(packed) || packed.size <= MAGIC.size + IV_BYTES) {
            throw IOException("持久化快照头已损坏")
        }
        val iv = packed.copyOfRange(MAGIC.size, MAGIC.size + IV_BYTES)
        val encrypted = packed.copyOfRange(MAGIC.size + IV_BYTES, packed.size)
        return Cipher.getInstance(ALGORITHM).run {
            init(Cipher.DECRYPT_MODE, key(context), GCMParameterSpec(TAG_BITS, iv))
            doFinal(encrypted)
        }
    }

    fun encryptFile(context: Context, source: File, destination: File) {
        val cipher = Cipher.getInstance(ALGORITHM).apply {
            init(Cipher.ENCRYPT_MODE, key(context))
        }
        destination.parentFile?.mkdirs()
        FileOutputStream(destination).buffered().use { output ->
            output.write(MAGIC)
            output.write(cipher.iv)
            CipherOutputStream(output, cipher).use { encrypted ->
                FileInputStream(source).buffered().use { it.copyTo(encrypted) }
            }
        }
    }

    fun decryptFile(context: Context, source: File, destination: File) {
        FileInputStream(source).buffered().use { input ->
            val magic = ByteArray(MAGIC.size)
            if (input.read(magic) != MAGIC.size || !magic.contentEquals(MAGIC)) {
                throw IOException("持久化附件头已损坏")
            }
            val iv = ByteArray(IV_BYTES)
            if (input.read(iv) != IV_BYTES) throw IOException("持久化附件缺少 IV")
            val cipher = Cipher.getInstance(ALGORITHM).apply {
                init(Cipher.DECRYPT_MODE, key(context), GCMParameterSpec(TAG_BITS, iv))
            }
            destination.parentFile?.mkdirs()
            CipherInputStream(input, cipher).use { decrypted ->
                destination.outputStream().buffered().use { decrypted.copyTo(it) }
            }
        }
    }

    fun atomicWrite(context: Context, plain: ByteArray, destination: File) {
        val temp = File(destination.parentFile, "${destination.name}.new")
        try {
            temp.writeBytes(encrypt(context, plain))
            atomicMove(temp, destination)
        } finally {
            temp.delete()
        }
    }

    fun atomicEncryptFile(context: Context, source: File, destination: File) {
        val temp = File(destination.parentFile, "${destination.name}.new")
        try {
            encryptFile(context, source, temp)
            atomicMove(temp, destination)
        } finally {
            temp.delete()
        }
    }

    fun hasMagic(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.size + IV_BYTES &&
            MAGIC.indices.all { bytes[it] == MAGIC[it] }

    internal fun deriveKey(
        packageName: String,
        androidId: String,
        signingSha256: String,
        salt: ByteArray,
    ): ByteArray {
        val material = "$packageName\u0000$androidId\u0000$signingSha256"
        val factory = runCatching {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        }.getOrElse {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        }
        val spec = PBEKeySpec(material.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    @SuppressLint("HardwareIds")
    private fun key(context: Context): SecretKeySpec {
        val salt = salt()
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ).orEmpty()
        val signing = signingCertificateSha256(context)
        return SecretKeySpec(
            deriveKey(context.packageName, androidId, signing, salt),
            "AES",
        )
    }

    private fun salt(): ByteArray {
        val file = File(StorageLocations.persistentRoot(), "v2.salt")
        if (file.isFile && file.length() in 16L..128L) return file.readBytes()
        file.parentFile?.mkdirs()
        val value = ByteArray(32).also(SecureRandom()::nextBytes)
        val temp = File(file.parentFile, "${file.name}.new")
        try {
            temp.writeBytes(value)
            atomicMove(temp, file)
        } finally {
            temp.delete()
        }
        return value
    }

    @Suppress("DEPRECATION")
    private fun signingCertificateSha256(context: Context): String {
        val pm = context.packageManager
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                .signatures
                ?.firstOrNull()
                ?.toByteArray()
        }
        if (signature == null) {
            Log.e(TAG, "无法读取应用签名，持久化快照将使用空签名材料")
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(signature ?: ByteArray(0))
            .joinToString("") { "%02x".format(it) }
    }

    private fun atomicMove(source: File, destination: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } else {
            if (destination.exists() && !destination.delete()) {
                throw IOException("无法替换 ${destination.name}")
            }
            if (!source.renameTo(destination)) throw IOException("无法保存 ${destination.name}")
        }
    }

    private val MAGIC = PREFIX.toByteArray(Charsets.US_ASCII)
}
