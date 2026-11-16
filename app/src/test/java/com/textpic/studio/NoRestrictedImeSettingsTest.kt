package com.textpic.studio

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Android 14+ throws SecurityException when an app targeting SDK 34 or higher reads
 * the restricted ime settings key. Guard against reintroducing that crash.
 */
class NoRestrictedImeSettingsTest {
    @Test
    fun `main sources do not read the restricted enabled input methods setting`() {
        val root = projectRoot()
        // Split so this test file itself is never a match.
        val restrictedKey = "ENABLED_INPUT" + "_METHODS"
        val offenders = listOf("app/src/main/java", "core/src/main/java")
            .map { File(root, it) }
            .filter { it.isDirectory }
            .flatMap { directory ->
                directory.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .toList()
            }
            .filter { file -> file.readText().contains(restrictedKey) }
            .map { file -> file.relativeTo(root).invariantSeparatorsPath }
            .sorted()

        assertWithMessage(
            "Restricted settings key $restrictedKey must not be read; use " +
                "InputMethodManager.getEnabledInputMethodList() instead.",
        ).that(offenders).isEmpty()
    }

    private fun projectRoot(): File {
        var directory: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (directory != null) {
            if (File(directory, "settings.gradle.kts").isFile) return directory
            directory = directory.parentFile
        }
        error("Could not locate the project root from ${System.getProperty("user.dir")}")
    }
}
