package com.textpic.studio.share

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.textpic.studio.MainActivity

class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            ?: intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)
        val text = raw?.toString()
        if (!text.isNullOrBlank()) {
            runCatching {
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra(MainActivity.EXTRA_INCOMING_TEXT, text)
                    },
                )
            }
        }
        finish()
    }
}
