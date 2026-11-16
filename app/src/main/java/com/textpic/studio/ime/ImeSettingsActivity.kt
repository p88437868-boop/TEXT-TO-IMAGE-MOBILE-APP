package com.textpic.studio.ime

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.textpic.studio.MainActivity

class ImeSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { startActivity(intent(this)) }
        finish()
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
            }
    }
}
