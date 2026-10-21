package vn.smartcleaner.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { DARK, LIGHT, SYSTEM }

class Prefs(c: Context) {
    private val sp = c.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    private val _theme = MutableStateFlow(runCatching { ThemeMode.valueOf(sp.getString("theme", "DARK")!!) }.getOrDefault(ThemeMode.DARK))
    val theme = _theme.asStateFlow()
    fun setTheme(t: ThemeMode) { sp.edit().putString("theme", t.name).apply(); _theme.value = t }
    var onboarded: Boolean get() = sp.getBoolean("onb", false); set(v) { sp.edit().putBoolean("onb", v).apply() }
    var bgScan: Boolean get() = sp.getBoolean("bg", true); set(v) { sp.edit().putBoolean("bg", v).apply() }
    var treeUri: String? get() = sp.getString("tree", null); set(v) { sp.edit().putString("tree", v).apply() }
    var lastReclaimable: Long get() = sp.getLong("lastRec", 0); set(v) { sp.edit().putLong("lastRec", v).apply() }
    var similarThreshold: Int get() = sp.getInt("simT", 10); set(v) { sp.edit().putInt("simT", v).apply() }
}
