package com.textpic.studio

import android.content.Context
import com.textpic.core.BubbleRenderer
import com.textpic.studio.data.db.TextPicDatabase
import com.textpic.studio.data.license.LicenseApi
import com.textpic.studio.data.license.LicenseService
import com.textpic.studio.data.prefs.FontManager
import com.textpic.studio.data.prefs.SettingsRepository
import com.textpic.studio.data.repo.ImageStore
import com.textpic.studio.data.repo.MessageRepository
import com.textpic.studio.data.repo.TemplateRepository
import com.textpic.studio.data.update.UpdateApi
import com.textpic.studio.data.update.UpdateManager
import com.textpic.studio.domain.LicenseGate
import com.textpic.studio.domain.UsageGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database: TextPicDatabase = TextPicDatabase.build(appContext)
    val settingsRepository = SettingsRepository(appContext)
    val fontManager = FontManager(appContext)
    val imageStore = ImageStore(appContext)
    val messageRepository = MessageRepository(database.messageDao(), imageStore)
    val templateRepository = TemplateRepository(database.templateDao())
    val renderer = BubbleRenderer(fontManager)
    val licenseService = LicenseService(
        context = appContext,
        api = LicenseApi(BuildConfig.LICENSE_SERVER_URL),
    )
    val licenseGate = LicenseGate(
        licensedProvider = { licenseService.state.value.licensed },
        quotaProvider = { licenseService.state.value.quota },
        pendingProvider = { licenseService.state.value.pendingUsage },
    )
    val usageGate = UsageGate(
        snapshot = { licenseService.state.value },
        onRender = { licenseService.noteRender() },
    )
    val updateManager = UpdateManager(
        context = appContext,
        api = UpdateApi(BuildConfig.LICENSE_SERVER_URL),
        scope = appScope,
    )
}
