package com.dante.veil

enum class ShizukuState { Checking, NotRunning, NeedPermission, Ready }

enum class Kind { ICON, APP }

/** One launcher entry (a drawer icon): an activity or activity-alias. */
data class IconEntry(
    val pkg: String,
    val cls: String,
    val label: String,
    val enabled: Boolean
) {
    val key: String get() = "$pkg/$cls"
}

data class AppEntry(
    val pkg: String,
    val label: String,
    val system: Boolean,
    val icons: List<IconEntry>
)

data class HiddenItem(
    val kind: Kind,
    val pkg: String,
    val title: String,
    val subtitle: String,
    val key: String
)
