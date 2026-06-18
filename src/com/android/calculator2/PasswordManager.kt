package com.android.calculator2

import android.content.Context
import android.content.SharedPreferences

class PasswordManager private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "vault_prefs"
        private const val KEY_PASSWORD = "vault_password"
        private const val DEFAULT_PASSWORD = "1234"

        @Volatile
        private var instance: PasswordManager? = null

        @JvmStatic
        fun getInstance(context: Context): PasswordManager {
            return instance ?: synchronized(this) {
                instance ?: PasswordManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun checkPassword(input: String): Boolean {
        return input == getPassword()
    }

    fun getPassword(): String {
        return prefs.getString(KEY_PASSWORD, DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD
    }

    fun setPassword(newPassword: String) {
        prefs.edit().putString(KEY_PASSWORD, newPassword).apply()
    }
}