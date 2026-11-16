package com.textpic.studio.ime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

object ImeStatus {
    val RECENT_ACTIVITY_WINDOW_MS = 10L * 60L * 1000L
    const val CHECK_FAILED_MESSAGE = "Couldn't check, tap to continue."

    fun component(context: Context): ComponentName =
        ComponentName(context, TextPicIME::class.java)

    fun isEnabled(context: Context): Result<Boolean> = runCatching {
        val manager = context.getSystemService(InputMethodManager::class.java)
            ?: error("Keyboard service unavailable.")
        val component = component(context)
        val expectedId = component.flattenToShortString()
        manager.getEnabledInputMethodList().any { info ->
            info.packageName == component.packageName &&
                (info.serviceName == component.className ||
                    info.id == expectedId ||
                    info.id == component.flattenToString())
        }
    }

    fun selectedBySystem(context: Context): Result<Boolean> = runCatching {
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        ) ?: return@runCatching false
        val component = component(context)
        val selected = ComponentName.unflattenFromString(raw)
        if (selected != null) {
            selected.packageName == component.packageName &&
                selected.className == component.className
        } else {
            raw == component.flattenToString() || raw == component.flattenToShortString()
        }
    }

    fun isRecentlyActive(lastImeActiveAt: Long, now: Long = System.currentTimeMillis()): Boolean =
        lastImeActiveAt > 0L &&
            now >= lastImeActiveAt &&
            now - lastImeActiveAt <= RECENT_ACTIVITY_WINDOW_MS

    fun resolveSelected(
        lastImeActiveAt: Long,
        systemSelected: Boolean?,
        now: Long = System.currentTimeMillis(),
    ): Boolean? = when {
        isRecentlyActive(lastImeActiveAt, now) -> true
        systemSelected != null -> systemSelected
        else -> null
    }

    fun openKeyboardSettings(context: Context): Result<Unit> = runCatching {
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    fun showKeyboardPicker(context: Context): Result<Unit> = runCatching {
        val manager = context.getSystemService(InputMethodManager::class.java)
            ?: error("Keyboard service unavailable.")
        manager.showInputMethodPicker()
    }
}
