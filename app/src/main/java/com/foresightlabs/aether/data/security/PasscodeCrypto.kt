package com.foresightlabs.aether.data.security

import com.foresightlabs.aether.domain.security.AppLockVerifier
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Derives and checks the one-way verifier Aether's local passcode is stored as.
 *
 * The raw PIN is never persisted -- only a salted PBKDF2-HMAC-SHA256 hash of it,
 * so recovering the digits from disk requires brute-forcing the derivation
 * itself, not just reading a file. [CURRENT_VERSION] is stamped onto every
 * verifier so a future change to the algorithm or work factor can recognise
 * and migrate (or reject) verifiers written by an older version instead of
 * silently misreading them.
 */
object PasscodeCrypto {

    const val CURRENT_VERSION = 1

    /**
     * Iterations for PBKDF2-HMAC-SHA256. OWASP's 2023 guidance floor for this
     * algorithm is 600,000; this sits comfortably above it while still
     * resolving in well under 100ms on the class of hardware Aether targets
     * (minSdk 24), since it runs once per unlock attempt on the main flow,
     * not in a hot loop.
     */
    private const val ITERATIONS = 650_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    private val secureRandom = SecureRandom()

    /** Builds a fresh verifier for [pin] with a new random salt. */
    fun createVerifier(pin: CharArray): AppLockVerifier {
        val salt = ByteArray(SALT_LENGTH_BYTES).also(secureRandom::nextBytes)
        val hash = derive(pin, salt, ITERATIONS)
        return AppLockVerifier(
            version = CURRENT_VERSION,
            saltBase64 = Base64Util.encode(salt),
            hashBase64 = Base64Util.encode(hash),
            iterations = ITERATIONS,
            pinLength = pin.size
        )
    }

    /**
     * Checks [pin] against [verifier] in constant time with respect to how
     * much of the derived hash matches, so a timing side channel can't leak
     * how close a guess was. An unrecognised [AppLockVerifier.version] always
     * fails closed rather than guessing at how to interpret it.
     */
    fun verify(pin: CharArray, verifier: AppLockVerifier): Boolean {
        if (verifier.version != CURRENT_VERSION) return false
        val salt = Base64Util.decode(verifier.saltBase64) ?: return false
        val expected = Base64Util.decode(verifier.hashBase64) ?: return false
        val actual = derive(pin, salt, verifier.iterations)
        return constantTimeEquals(actual, expected)
    }

    private fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
}

private object Base64Util {
    fun encode(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    fun decode(value: String): ByteArray? = try {
        android.util.Base64.decode(value, android.util.Base64.NO_WRAP)
    } catch (_: IllegalArgumentException) {
        null
    }
}
