package com.example.campuswallet

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Helper object for securely handling user passwords.
 * Passwords are never stored in plain text. Instead, each password is combined
 * with a random salt and hashed using PBKDF2, and only the salt and hash are saved.
 */
object PasswordHelper {

    /**
     * Generates a random 16-byte salt and returns it as a Base64 string.
     * A unique salt per user means two identical passwords produce different hashes.
     */
    fun createSalt(): String {
        // Allocate 16 bytes to hold the salt
        val salt = ByteArray(16)

        // Fill the array with cryptographically secure random bytes
        SecureRandom().nextBytes(salt)

        // Convert the bytes to a text string so it can be stored easily (NO_WRAP avoids line breaks)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    /**
     * Hashes a password with the given salt using PBKDF2 (HMAC-SHA256).
     * Returns the resulting hash as a Base64 string.
     */
    fun hashPassword(password: String, saltString: String): String {
        // Convert the stored Base64 salt back into raw bytes
        val salt = Base64.decode(saltString, Base64.NO_WRAP)

        // Define the hashing settings: the password, the salt,
        // 120000 iterations (makes brute-force guessing slower) and a 256-bit output length
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            120000,
            256
        )

        return try {
            // Get the PBKDF2 algorithm implementation
            val factory = SecretKeyFactory.getInstance(
                "PBKDF2WithHmacSHA256"
            )

            // Run the algorithm and extract the raw hash bytes
            val hash = factory.generateSecret(spec).encoded

            // Convert the hash to a Base64 string for storage
            Base64.encodeToString(hash, Base64.NO_WRAP)
        } finally {
            // Always wipe the password from memory, even if an error occurs
            spec.clearPassword()
        }
    }

    /**
     * Checks whether a password entered at login matches the stored hash.
     * Returns true if they match, false otherwise.
     */
    fun verifyPassword(
        password: String,
        salt: String,
        storedHash: String
    ): Boolean {

        // Hash the entered password using the same salt that was used at registration
        val newHash = hashPassword(password, salt)

        // Compare the two hashes using a constant-time check, which prevents
        // timing attacks that could reveal how much of the hash was correct
        return MessageDigest.isEqual(
            Base64.decode(newHash, Base64.NO_WRAP),
            Base64.decode(storedHash, Base64.NO_WRAP)
        )
    }
}