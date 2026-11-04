package com.videobrowsing.honeysuckle.core.datastore.account

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 登录凭据的落盘加密口(Android Keystore + AES-GCM)。
 *
 * 盘上的 `cookie` 不再是一串能直接拿去复用的 `SESSDATA`:
 *  - 密钥由 Keystore 生成并保管,私钥材料不出安全硬件/系统密钥库,应用只能"请求加解密";
 *  - 每次加密用新的随机 IV,GCM 自带完整性校验 —— 密文被改过就解不出来,不会退回半截凭证;
 *  - 密文带 [PREFIX] 版本前缀,旧版本写下的明文仍能透明读回(见 [decrypt]),
 *    下一次登录或 [AccountPreferenceRepository.migrateCredentials] 时改写成密文。
 *
 * **不引 `security-crypto`**:这里只需要一个 AES-GCM 密钥与两行 Cipher 调用,
 * 多引一个库会把 `minSdk` 与依赖面一起抬高,而它内部做的也是同一件事。
 *
 * 解密失败(密钥被系统吊销、密文损坏)一律回空串:凭证已经不可恢复,
 * 让用户重新登录比让应用在启动读盘时崩掉更诚实。
 */
@Singleton
class CredentialCipher @Inject constructor() {

    /** 是否已是本类写出的密文;旧明文返回 false,迁移逻辑据此决定要不要改写 */
    fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)

    fun encrypt(plain: String): String {
        if (plain.isBlank()) return plain
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(iv + body, Base64.NO_WRAP)
    }

    /** 旧明文原样返回;带前缀的密文解不开时回空串(见类注释) */
    fun decrypt(stored: String): String {
        if (stored.isBlank() || !isEncrypted(stored)) return stored
        return runCatching {
            val payload = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            if (payload.size <= IV_BYTES) return@runCatching ""
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES),
            )
            String(cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES), Charsets.UTF_8)
        }.getOrDefault("")
    }

    private val key: SecretKey by lazy { loadOrCreateKey() }

    private fun loadOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "hsy.account.credential.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** 版本前缀:将来换算法/换密钥时靠它分流,不必猜这串是不是密文 */
        const val PREFIX = "v1:"

        /** GCM 推荐的 96-bit IV 与 128-bit tag */
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}
