package com.livescore.floating

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("apiKey", "") ?: ""
        set(v) = sp.edit().putString("apiKey", v.trim()).apply()

    /** 0 = system, 1 = light, 2 = dark */
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) = sp.edit().putInt("theme", v).apply()

    /** widget opacity 30..100 */
    var opacity: Int
        get() = sp.getInt("opacity", 95)
        set(v) = sp.edit().putInt("opacity", v).apply()

    /** polling interval in seconds (used only with a real API key) */
    var pollSec: Int
        get() = sp.getInt("pollSec", 30)
        set(v) = sp.edit().putInt("pollSec", v).apply()

    var notifyGoals: Boolean
        get() = sp.getBoolean("notifyGoals", true)
        set(v) = sp.edit().putBoolean("notifyGoals", v).apply()

    var pinnedId: Int
        get() = sp.getInt("pinnedId", -1)
        set(v) = sp.edit().putInt("pinnedId", v).apply()

    var overlayX: Int
        get() = sp.getInt("overlayX", 24)
        set(v) = sp.edit().putInt("overlayX", v).apply()

    var overlayY: Int
        get() = sp.getInt("overlayY", 200)
        set(v) = sp.edit().putInt("overlayY", v).apply()
}
