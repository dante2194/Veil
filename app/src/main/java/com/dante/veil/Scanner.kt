package com.dante.veil

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("veil", Context.MODE_PRIVATE)

    fun icons(): Set<String> = sp.getStringSet("icons", emptySet())?.toSet() ?: emptySet()
    fun apps(): Set<String> = sp.getStringSet("apps", emptySet())?.toSet() ?: emptySet()

    fun putIcons(s: Set<String>) {
        sp.edit().putStringSet("icons", HashSet(s)).apply()
    }

    fun putApps(s: Set<String>) {
        sp.edit().putStringSet("apps", HashSet(s)).apply()
    }
}

object Scanner {

    /**
     * Lists every launcher entry on the device, including ones that are
     * currently disabled (that's what MATCH_DISABLED_COMPONENTS is for).
     */
    @Suppress("DEPRECATION")
    fun scan(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found = pm.queryIntentActivities(intent, PackageManager.MATCH_DISABLED_COMPONENTS)

        return found
            .filter { it.activityInfo.packageName != ctx.packageName }
            .groupBy { it.activityInfo.packageName }
            .map { (pkg, list) ->
                val appInfo = list.first().activityInfo.applicationInfo
                val icons = list.map { ri ->
                    val ai = ri.activityInfo
                    val state = try {
                        pm.getComponentEnabledSetting(ComponentName(ai.packageName, ai.name))
                    } catch (_: Throwable) {
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                    }
                    val enabled = when (state) {
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
                        else -> ai.enabled
                    }
                    IconEntry(pkg, ai.name, ri.loadLabel(pm).toString(), enabled)
                }.sortedBy { it.label.lowercase() }

                AppEntry(
                    pkg = pkg,
                    label = appInfo.loadLabel(pm).toString(),
                    system = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    icons = icons
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}
