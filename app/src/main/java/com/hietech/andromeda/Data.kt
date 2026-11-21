package com.hietech.andromeda

import android.app.Application
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

val Context.store by preferencesDataStore("andromeda")
private val HIDDEN = stringSetPreferencesKey("hidden")
private val FAVS = stringSetPreferencesKey("favs")
private val LIGHT = booleanPreferencesKey("light")
private val ACCENT = intPreferencesKey("accent")
private val GLASS = floatPreferencesKey("glass")
private val LAYOUT = stringPreferencesKey("layout")
private val FOCUS = stringPreferencesKey("focus")
private val ACTIVE = intPreferencesKey("focus_active")
private val OVR = longPreferencesKey("focus_override")
private val PIN = stringPreferencesKey("pin")
private val UPKG = stringPreferencesKey("universe_pkg")
private val USCHEME = stringPreferencesKey("universe_scheme")
private val LONGP = intPreferencesKey("long_action")
private val DOWN = intPreferencesKey("down_action")

// Focus profile. start/end are minutes of day, -1 = no schedule. pkgs = packages blocked from launching in Andromeda.
data class Profile(val name: String, val pkgs: Set<String>, val start: Int, val end: Int) {
    fun scheduledAt(min: Int): Boolean = start >= 0 && end >= 0 && start != end &&
        (if (start < end) min in start until end else (min >= start || min < end))
}
fun parseProfiles(s: String?): List<Profile> =
    if (s.isNullOrEmpty()) listOf("Study", "Work", "Personal", "Night", "Custom").map { Profile(it, emptySet(), -1, -1) }
    else s.split("\n").mapNotNull {
        val p = it.split("|")
        if (p.size < 4) null
        else Profile(p[0], p[1].split(";").filter { x -> x.isNotEmpty() }.toSet(), p[2].toIntOrNull() ?: -1, p[3].toIntOrNull() ?: -1)
    }
fun serProfiles(l: List<Profile>) = l.joinToString("\n") { "${it.name}|${it.pkgs.joinToString(";")}|${it.start}|${it.end}" }

// Layout: pages split by "\n", items by ",". Item = app component key, or "F~name~key;key" for a folder.
fun parseLayout(s: String?): List<List<String>> =
    if (s.isNullOrEmpty()) listOf(emptyList(), emptyList())
    else s.split("\n").map { if (it.isEmpty()) emptyList() else it.split(",") }
fun serLayout(l: List<List<String>>) = l.joinToString("\n") { it.joinToString(",") }
fun folderKeys(item: String) = item.split("~").getOrElse(2) { "" }.split(";").filter { it.isNotEmpty() }

data class AppItem(val label: String, val component: ComponentName, val icon: ImageBitmap) {
    val key: String get() = component.flattenToString()
    val pkg: String get() = component.packageName
}

class LauncherViewModel(private val app: Application) : AndroidViewModel(app) {
    private val la = app.getSystemService(LauncherApps::class.java)
    private val all = MutableStateFlow<List<AppItem>>(emptyList())

    val hiddenKeys = app.store.data.map { it[HIDDEN] ?: emptySet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val favKeys = app.store.data.map { it[FAVS] ?: emptySet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val lightMode = app.store.data.map { it[LIGHT] ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val profiles = app.store.data.map { parseProfiles(it[FOCUS]) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, parseProfiles(null))
    val activeManual = app.store.data.map { it[ACTIVE] ?: -1 }.stateIn(viewModelScope, SharingStarted.Eagerly, -1)
    val overrideUntil = app.store.data.map { it[OVR] ?: 0L }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    val blocked = MutableStateFlow<Pair<AppItem, String>?>(null)

    /** Manual profile wins, otherwise the first profile whose schedule covers now. Null during emergency override. */
    fun activeProfile(): Profile? {
        if (System.currentTimeMillis() < overrideUntil.value) return null
        val l = profiles.value
        val m = activeManual.value
        if (m in l.indices) return l[m]
        val t = java.time.LocalTime.now()
        val min = t.hour * 60 + t.minute
        return l.firstOrNull { it.scheduledAt(min) }
    }
    fun setActive(i: Int) { viewModelScope.launch { app.store.edit { it[ACTIVE] = i; it[OVR] = 0L } } }
    fun updateProfile(i: Int, p: Profile) {
        viewModelScope.launch {
            app.store.edit {
                val l = parseProfiles(it[FOCUS]).toMutableList()
                if (i in l.indices) { l[i] = p; it[FOCUS] = serProfiles(l) }
            }
        }
    }
    fun emergencyOverride() {
        viewModelScope.launch { app.store.edit { it[OVR] = System.currentTimeMillis() + 5 * 60_000L } }
        blocked.value = null
    }

    val pin = app.store.data.map { it[PIN] ?: "" }.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    private fun hashPin(salt: String, p: String) =
        java.security.MessageDigest.getInstance("SHA-256").digest((salt + p).toByteArray()).joinToString("") { "%02x".format(it) }
    fun setPin(p: String) {
        val salt = java.util.UUID.randomUUID().toString()
        viewModelScope.launch { app.store.edit { it[PIN] = "$salt:${hashPin(salt, p)}" } }
    }
    fun clearPin() { viewModelScope.launch { app.store.edit { it.remove(PIN) } } }
    fun checkPin(p: String): Boolean {
        val parts = pin.value.split(":")
        if (parts.size != 2) return true
        return hashPin(parts[0], p) == parts[1]
    }

    val universePkg = app.store.data.map { it[UPKG] ?: "" }.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val universeScheme = app.store.data.map { it[USCHEME] ?: "" }.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun saveUniverse(pkg: String, scheme: String) {
        viewModelScope.launch { app.store.edit { it[UPKG] = pkg.trim(); it[USCHEME] = scheme.trim() } }
    }
    fun launchPackage(pkg: String): Boolean {
        val a = all.value.firstOrNull { it.pkg == pkg } ?: return false
        launch(a)
        return true
    }
    fun universe(pkg: String = universePkg.value, scheme: String = universeScheme.value): UniverseBridge =
        IntentUniverseBridge(app, pkg.trim(), scheme.trim(), { p -> all.value.any { it.pkg == p } }, ::launchPackage)

    val host = AppWidgetHost(app, 1024)
    val mgr: AppWidgetManager = AppWidgetManager.getInstance(app)
    @Volatile var currentPage = 0
    // Long-press action: 0 = Settings, 1 = Add widget. Swipe-down: 0 = none, 1 = Settings, 2 = Add widget.
    val longAction = app.store.data.map { it[LONGP] ?: 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val downAction = app.store.data.map { it[DOWN] ?: 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    fun setLongAction(v: Int) { viewModelScope.launch { app.store.edit { it[LONGP] = v } } }
    fun setDownAction(v: Int) { viewModelScope.launch { app.store.edit { it[DOWN] = v } } }

    val layout = app.store.data.map { parseLayout(it[LAYOUT]) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, parseLayout(null))
    val accentIdx = app.store.data.map { it[ACCENT] ?: 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val glass = app.store.data.map { it[GLASS] ?: 0.08f }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.08f)

    val visible = combine(all, hiddenKeys) { a, h -> a.filter { it.key !in h } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val hiddenApps = combine(all, hiddenKeys) { a, h -> a.filter { it.key in h } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val dock = combine(visible, favKeys) { a, f -> a.filter { it.key in f }.take(5) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val cb = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = load()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = load()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = load()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = load()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = load()
    }

    init { load(); la.registerCallback(cb) }
    override fun onCleared() { la.unregisterCallback(cb) }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = la.getActivityList(null, Process.myUserHandle())
                .filter { it.componentName.packageName != app.packageName }
                .map {
                    AppItem(
                        it.label.toString(), it.componentName,
                        it.getIcon(0).toBitmap(96, 96).asImageBitmap()
                    )
                }
                .sortedBy { it.label.lowercase() }
            all.value = list
        }
    }

    fun launch(a: AppItem) {
        val p = activeProfile()
        if (p != null && a.pkg in p.pkgs) { blocked.value = a to p.name; return }
        try { la.startMainActivity(a.component, Process.myUserHandle(), null, null) } catch (_: Exception) {}
    }

    private fun editSet(key: Preferences.Key<Set<String>>, f: (Set<String>) -> Set<String>) {
        viewModelScope.launch { app.store.edit { it[key] = f(it[key] ?: emptySet()) } }
    }
    fun hide(a: AppItem) = editSet(HIDDEN) { it + a.key }
    fun restore(a: AppItem) = editSet(HIDDEN) { it - a.key }
    fun toggleFav(a: AppItem) = editSet(FAVS) { if (a.key in it) it - a.key else it + a.key }
    fun setLight(v: Boolean) { viewModelScope.launch { app.store.edit { it[LIGHT] = v } } }

    private fun editLayout(f: (List<List<String>>) -> List<List<String>>) {
        viewModelScope.launch { app.store.edit { it[LAYOUT] = serLayout(f(parseLayout(it[LAYOUT]))) } }
    }
    fun addToPage(a: AppItem, page: Int) = editLayout { l ->
        if (l.any { p -> p.contains(a.key) }) l
        else l.mapIndexed { i, p -> if (i == page.coerceIn(0, l.size - 1)) p + a.key else p }
    }
    fun removeItem(item: String) = editLayout { l -> l.map { it - item } }
    fun addPage() = editLayout { it + listOf(emptyList<String>()) }
    fun dissolveFolder(item: String) = editLayout { l ->
        l.map { p -> p.flatMap { x -> if (x == item) folderKeys(item) else listOf(x) } }
    }
    fun intoFolder(a: AppItem, rawName: String) = editLayout { l ->
        val name = rawName.filter { it !in ",~;\n" }.trim().ifEmpty { "Folder" }
        val pi = l.indexOfFirst { p -> p.contains(a.key) }.let { if (it < 0) 0 else it }
        l.map { p -> p - a.key }.mapIndexed { i, p ->
            if (i != pi) p else {
                val idx = p.indexOfFirst { x -> x.startsWith("F~$name~") }
                if (idx >= 0) p.toMutableList().also { m -> m[idx] = m[idx] + ";" + a.key }
                else p + "F~$name~${a.key}"
            }
        }
    }
    fun addWidget(id: Int) = editLayout { l ->
        l.mapIndexed { i, p -> if (i == currentPage.coerceIn(0, l.size - 1)) p + "W~$id" else p }
    }
    fun removeWidget(id: Int) {
        try { host.deleteAppWidgetId(id) } catch (_: Exception) {}
        removeItem("W~$id")
    }
    fun setAccent(i: Int) { viewModelScope.launch { app.store.edit { it[ACCENT] = i } } }
    fun setGlass(v: Float) { viewModelScope.launch { app.store.edit { it[GLASS] = v } } }
}
