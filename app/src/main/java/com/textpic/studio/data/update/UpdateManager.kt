package com.textpic.studio.data.update

import android.content.Context
import com.textpic.studio.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Checks for, downloads and verifies updates.
 *
 * Chunks are fetched one at a time (512 KB) so a phone on a weak connection can
 * resume, and nothing is ever installed before its per-chunk hashes, its whole
 * file hash and its size match the signed manifest.
 */
class UpdateManager(
    context: Context,
    private val api: UpdateApi,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    private val clock: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope,
) {
    data class UiState(
        val info: UpdateInfo? = null,
        val mandatory: Boolean = false,
        val checking: Boolean = false,
        val downloading: Boolean = false,
        val progress: Float = 0f,
        val ready: File? = null,
        val message: String? = null,
        val dismissed: Boolean = false,
    ) {
        val hasUpdate: Boolean get() = info != null
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("textpic_updates", Context.MODE_PRIVATE)
    private val updateDir = File(appContext.cacheDir, "updates")
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val lastCheckAt: Long get() = prefs.getLong("last_check_at", 0L)
    private val suppressedUntil: Long get() = prefs.getLong("suppressed_until", 0L)

    /** Called when the app starts: at most one check every six hours. */
    fun checkIfDue() {
        if (!api.isConfigured) return
        if (!UpdateRules.shouldCheck(lastCheckAt, clock())) return
        if (UpdateRules.isSuppressed(suppressedUntil, clock())) return
        check(force = false)
    }

    fun check(force: Boolean = true) {
        scope.launch {
            _state.value = _state.value.copy(checking = true, message = null)
            val result = withContext(Dispatchers.IO) { api.latest(currentVersionCode) }
            prefs.edit().putLong("last_check_at", clock()).apply()
            _state.value = when (result) {
                is UpdateCheck.Available -> {
                    if (!force && UpdateRules.isSuppressed(suppressedUntil, clock())) {
                        _state.value.copy(checking = false)
                    } else {
                        _state.value.copy(
                            info = result.info,
                            mandatory = result.mandatory,
                            checking = false,
                            dismissed = false,
                            message = null,
                        )
                    }
                }
                UpdateCheck.UpToDate -> _state.value.copy(
                    info = null,
                    mandatory = false,
                    checking = false,
                    message = "TextPic is up to date.",
                )
                is UpdateCheck.Failed -> _state.value.copy(
                    checking = false,
                    message = result.detail,
                )
            }
        }
    }

    /** "Later" hides the dialog for a day (never for a mandatory update). */
    fun later() {
        prefs.edit()
            .putLong("suppressed_until", clock() + UpdateRules.SUPPRESS_MS)
            .apply()
        _state.value = _state.value.copy(dismissed = true)
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun download() {
        val info = _state.value.info ?: return
        if (_state.value.downloading) return
        scope.launch {
            _state.value = _state.value.copy(downloading = true, progress = 0f, message = null)
            val file = withContext(Dispatchers.IO) {
                runCatching { downloadVerified(info) { progress ->
                    _state.value = _state.value.copy(progress = progress)
                } }.getOrNull()
            }
            _state.value = if (file != null) {
                _state.value.copy(downloading = false, progress = 1f, ready = file)
            } else {
                _state.value.copy(
                    downloading = false,
                    message = "The download could not be verified. Please try again.",
                )
            }
        }
    }

    /** Downloads (or resumes) and checks every hash. Returns null on any mismatch. */
    fun downloadVerified(info: UpdateInfo, onProgress: (Float) -> Unit = {}): File? {
        updateDir.mkdirs()
        val target = File(updateDir, "TextPic-${info.versionName}.apk")
        val part = File(updateDir, "TextPic-${info.versionName}.part")
        var completedChunks = 0
        RandomAccessFile(part, "rw").use { file ->
            val chunkSize = info.chunkSize.toLong()
            for (index in 0 until info.chunkCount) {
                val offset = index * chunkSize
                val expected = minOf(chunkSize, info.sizeBytes - offset).toInt()
                if (expected <= 0) continue
                if (file.length() >= offset + expected) {
                    val existing = ByteArray(expected)
                    file.seek(offset)
                    file.readFully(existing)
                    if (sha256(existing) == info.chunkShas[index]) {
                        completedChunks = index + 1
                        onProgress(completedChunks.toFloat() / info.chunkCount)
                        continue
                    }
                }
                file.setLength(offset)
                val bytes = api.chunk(info, index) ?: return null
                if (bytes.size != expected || sha256(bytes) != info.chunkShas[index]) {
                    runCatching { part.delete() }
                    return null
                }
                file.seek(offset)
                file.write(bytes)
                completedChunks = index + 1
                onProgress(completedChunks.toFloat() / info.chunkCount)
            }
        }
        if (part.length() != info.sizeBytes) {
            runCatching { part.delete() }
            return null
        }
        if (sha256OfFile(part) != info.sha256) {
            runCatching { part.delete() }
            return null
        }
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            return null
        }
        // Keep only the newest download.
        updateDir.listFiles()?.forEach { file ->
            if (file.name != target.name) runCatching { file.delete() }
        }
        return target
    }

    fun installReady(): Boolean {
        val file = _state.value.ready ?: return false
        return UpdateInstaller.install(appContext, file)
    }

    /** Wipes leftovers from an interrupted download. */
    fun cleanup() {
        updateDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".part")) runCatching { file.delete() }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun sha256OfFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
