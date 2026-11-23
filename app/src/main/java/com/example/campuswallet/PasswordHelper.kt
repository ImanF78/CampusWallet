
package com.example.campuswallet

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHelper {

    fun createSalt(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hashPassword(password: String, saltString: String): String {
        val salt = Base64.decode(saltString, Base64.NO_WRAP)

        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            120000,
            256
        )

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded

        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun verifyPassword(password: String, salt: String, storedHash: String): Boolean {
        val newHash = hashPassword(password, salt)

        return MessageDigest.isEqual(
            newHash.toByteArray(),
            storedHash.toByteArray()
        )
    }
}

