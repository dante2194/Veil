package com.dante.veil

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)

    var shizuku by mutableStateOf(ShizukuState.Checking)
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
    var loading by mutableStateOf(true)
    var hiddenIcons by mutableStateOf(store.icons())
    var hiddenApps by mutableStateOf(store.apps())
    var drift by mutableStateOf(0)
    var query by mutableStateOf("")
    var showSystem by mutableStateOf(false)
    var multiOnly by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var pendingHide by mutableStateOf<AppEntry?>(null)

    val ready: Boolean get() = shizuku == ShizukuState.Ready
    val hiddenCount: Int get() = hiddenIcons.size + hiddenApps.size

    private val onBinder = Shizuku.OnBinderReceivedListener { updateState() }
    private val onDead = Shizuku.OnBinderDeadListener { updateState() }
    private val onPerm = Shizuku.OnRequestPermissionResultListener { _, _ -> updateState() }

    init {
        Shizuku.addBinderReceivedListenerSticky(onBinder)
        Shizuku.addBinderDeadListener(onDead)
        Shizuku.addRequestPermissionResultListener(onPerm)
        refresh()
    }

    override fun onCleared() {
        Shizuku.removeBinderReceivedListener(onBinder)
        Shizuku.removeBinderDeadListener(onDead)
        Shizuku.removeRequestPermissionResultListener(onPerm)
    }

    // ---------------- state ----------------

    fun updateState() {
        shizuku = when {
            !ShizukuShell.binderAlive() -> ShizukuState.NotRunning
            !ShizukuShell.granted() -> ShizukuState.NeedPermission
            else -> ShizukuState.Ready
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                message = "Permission was denied. Open Shizuku > Authorized apps and allow Veil."
            } else {
                Shizuku.requestPermission(1001)
            }
        } catch (t: Throwable) {
            message = t.message ?: "Shizuku is not available"
        }
    }

    fun refresh() {
        updateState()
        viewModelScope.launch {
            loading = true
            val list = scan()
            apps = list
            reconcile(list)
            loading = false
        }
    }

    private suspend fun scan(): List<AppEntry> =
        withContext(Dispatchers.IO) { Scanner.scan(getApplication<Application>()) }

    private suspend fun runCmd(cmd: String): ShellResult =
        withContext(Dispatchers.IO) { ShizukuShell.run(cmd) }

    private fun errText(r: ShellResult): String =
        r.err.ifBlank { r.out }.ifBlank { "unknown error" }.take(160)

    /** Drops records for uninstalled apps and counts icons that apps re-enabled on their own. */
    private fun reconcile(list: List<AppEntry>) {
        val byKey = HashMap<String, IconEntry>()
        for (a in list) for (i in a.icons) byKey[i.key] = i
        val hiddenPkgs = hiddenApps.map { it.substringBefore('|') }.toSet()
        val kept = hiddenIcons
            .filter { byKey.containsKey(it) || it.substringBefore('/') in hiddenPkgs }
            .toSet()
        if (kept != hiddenIcons) {
            hiddenIcons = kept
            store.putIcons(kept)
        }
        drift = kept.count { byKey[it]?.enabled == true }
    }

    // ---------------- queries for the UI ----------------

    fun visibleApps(): List<AppEntry> {
        val q = query.trim().lowercase()
        return apps.filter { a ->
            (showSystem || !a.system) &&
                (!multiOnly || a.icons.size > 1) &&
                (q.isEmpty() || a.label.lowercase().contains(q) || a.pkg.lowercase().contains(q))
        }
    }

    fun hiddenItems(): List<HiddenItem> {
        val byKey = HashMap<String, Pair<AppEntry, IconEntry>>()
        for (a in apps) for (i in a.icons) byKey[i.key] = a to i
        val out = ArrayList<HiddenItem>()
        for (key in hiddenIcons) {
            val pkg = key.substringBefore('/')
            val hit = byKey[key]
            val iconLabel = hit?.second?.label ?: key.substringAfterLast('.')
            out += HiddenItem(Kind.ICON, pkg, hit?.first?.label ?: pkg, "Icon: $iconLabel", key)
        }
        for (s in hiddenApps) {
            val pkg = s.substringBefore('|')
            val label = if (s.contains('|')) s.substringAfter('|') else pkg
            out += HiddenItem(Kind.APP, pkg, label, "Entire app hidden", pkg)
        }
        return out.sortedBy { it.title.lowercase() }
    }

    // ---------------- actions ----------------

    fun setIconVisible(icon: IconEntry, visible: Boolean) {
        if (!ready) return
        viewModelScope.launch {
            val cmd = "pm ${if (visible) "enable" else "disable"} --user 0 ${q(icon.key)}"
            val r = runCmd(cmd)
            val list = scan()
            apps = list
            val now = list.flatMap { it.icons }.firstOrNull { it.key == icon.key }
            if (now != null && now.enabled == visible) {
                val s = hiddenIcons.toMutableSet()
                if (visible) s.remove(icon.key) else s.add(icon.key)
                hiddenIcons = s
                store.putIcons(s)
                reconcile(list)
            } else {
                message = "Couldn't change ${icon.label}: ${errText(r)}"
            }
        }
    }

    fun showIconByKey(key: String, label: String) {
        setIconVisible(
            IconEntry(key.substringBefore('/'), key.substringAfter('/'), label, false),
            true
        )
    }

    fun setAppHidden(pkg: String, label: String, hide: Boolean) {
        if (!ready) return
        viewModelScope.launch {
            val r = runCmd("pm ${if (hide) "hide" else "unhide"} --user 0 ${q(pkg)}")
            val ok = if (r.out.contains("hidden state")) {
                r.out.contains("hidden state: $hide")
            } else {
                r.ok
            }
            if (ok) {
                val s = hiddenApps.filterNot { it.substringBefore('|') == pkg }.toMutableSet()
                if (hide) s.add("$pkg|$label")
                hiddenApps = s
                store.putApps(s)
                message = if (hide) "$label hidden" else "$label restored"
            } else {
                message = "Couldn't ${if (hide) "hide" else "unhide"} $label: ${errText(r)}"
            }
            val list = scan()
            apps = list
            reconcile(list)
        }
    }

    fun restoreAll() {
        if (!ready) return
        viewModelScope.launch {
            val script = buildString {
                hiddenApps.forEach { append("pm unhide --user 0 ${q(it.substringBefore('|'))}; ") }
                hiddenIcons.forEach { append("pm enable --user 0 ${q(it)}; ") }
                append("true")
            }
            val r = runCmd(script)
            val keepApps = hiddenApps.filter { s ->
                !r.out.contains("Package ${s.substringBefore('|')} new hidden state: false")
            }.toSet()
            hiddenApps = keepApps
            store.putApps(keepApps)

            val keepPkgs = keepApps.map { it.substringBefore('|') }.toSet()
            val list = scan()
            apps = list
            val still = hiddenIcons.filter { key ->
                keepPkgs.contains(key.substringBefore('/')) ||
                    list.any { a -> a.icons.any { it.key == key && !it.enabled } }
            }.toSet()
            hiddenIcons = still
            store.putIcons(still)
            reconcile(list)
            message = if (still.isEmpty() && keepApps.isEmpty()) {
                "Everything restored"
            } else {
                "Some items could not be restored"
            }
        }
    }

    fun reapply() {
        if (!ready) return
        viewModelScope.launch {
            val keys = hiddenIcons.filter { k ->
                apps.any { a -> a.icons.any { it.key == k && it.enabled } }
            }
            if (keys.isEmpty()) return@launch
            runCmd(keys.joinToString("; ") { "pm disable --user 0 ${q(it)}" })
            val list = scan()
            apps = list
            reconcile(list)
            message = if (drift == 0) {
                "Re-applied ${keys.size} icon(s)"
            } else {
                "Some icons could not be re-hidden"
            }
        }
    }
}
