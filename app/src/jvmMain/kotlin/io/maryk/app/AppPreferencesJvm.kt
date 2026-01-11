package io.maryk.app

import java.util.prefs.Preferences

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object AppPreferences {
    private val prefs = Preferences.userRoot().node("io.maryk.app.ui")

    actual fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    actual fun putBoolean(key: String, value: Boolean) {
        prefs.putBoolean(key, value)
    }

    actual fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    actual fun putInt(key: String, value: Int) {
        prefs.putInt(key, value)
    }

    actual fun getString(key: String, default: String): String = prefs.get(key, default)

    actual fun putString(key: String, value: String) {
        prefs.put(key, value)
    }
}
