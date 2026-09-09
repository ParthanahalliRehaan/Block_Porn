package com.example.block.security

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinManager {

    private const val PREFS = "block_security"

    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH = 256

    fun hasPin(context: Context): Boolean {
        return context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .contains(KEY_PIN_HASH)
    }

    fun setPin(
        context: Context,
        pin: String
    ) {

        require(pin.length >= 4) {
            "PIN must contain at least 4 digits"
        }

        val salt = ByteArray(16)

        SecureRandom().nextBytes(salt)

        val hash = hashPin(pin, salt)

        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(
                KEY_PIN_HASH,
                Base64.encodeToString(
                    hash,
                    Base64.NO_WRAP
                )
            )
            .putString(
                KEY_PIN_SALT,
                Base64.encodeToString(
                    salt,
                    Base64.NO_WRAP
                )
            )
            .apply()
    }

    fun verifyPin(
        context: Context,
        pin: String
    ): Boolean {

        val prefs =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

        val storedHash =
            prefs.getString(
                KEY_PIN_HASH,
                null
            ) ?: return false

        val storedSalt =
            prefs.getString(
                KEY_PIN_SALT,
                null
            ) ?: return false

        val salt =
            Base64.decode(
                storedSalt,
                Base64.NO_WRAP
            )

        val expected =
            Base64.decode(
                storedHash,
                Base64.NO_WRAP
            )

        val actual =
            hashPin(
                pin,
                salt
            )

        return constantTimeEquals(
            expected,
            actual
        )
    }

    private fun hashPin(
        pin: String,
        salt: ByteArray
    ): ByteArray {

        val spec = PBEKeySpec(
            pin.toCharArray(),
            salt,
            ITERATIONS,
            KEY_LENGTH
        )

        return try {

            SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded

        } finally {
            spec.clearPassword()
        }
    }

    private fun constantTimeEquals(
        a: ByteArray,
        b: ByteArray
    ): Boolean {

        if (a.size != b.size) {
            return false
        }

        var result = 0

        for (i in a.indices) {
            result =
                result or
                        (a[i].toInt() xor b[i].toInt())
        }

        return result == 0
    }
}