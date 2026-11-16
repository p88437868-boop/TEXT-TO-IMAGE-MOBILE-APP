package com.textpic.studio

import android.app.Application
import androidx.emoji2.text.DefaultEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import com.textpic.studio.data.RetentionScheduler
import com.textpic.studio.data.license.LicenseCheckScheduler
import kotlinx.coroutines.launch

class TextPicApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        runCatching {
            if (!EmojiCompat.isConfigured()) {
                DefaultEmojiCompatConfig.create(this)?.let(EmojiCompat::init)
            }
        }
        container.appScope.launch {
            runCatching { container.templateRepository.ensureSeeded() }
        }
        container.appScope.launch {
            runCatching { container.licenseService.refresh(online = true) }
        }
        RetentionScheduler.schedule(this)
        LicenseCheckScheduler.schedule(this)
    }
}
