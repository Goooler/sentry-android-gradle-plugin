package io.sentry.android.gradle

import io.sentry.android.gradle.util.error
import io.sentry.android.gradle.util.info
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.Properties
import org.gradle.api.Project

internal object SentryCliProvider {
    /**
     * Return the correct sentry-cli executable path to use for the given project.  This
     * will look for a sentry-cli executable in a local node_modules in case it was put
     * there by sentry-react-native or others before falling back to the global installation.
     */
    @JvmStatic
    fun getSentryCliPath(project: Project): String {
        // If a path is provided explicitly use that first.
        project.logger.info { "Searching cli from sentry.properties file..." }

        searchCliInPropertiesFile(project)?.let {
            project.logger.info { "cli Found: $it" }
            return@getSentryCliPath it
        } ?: project.logger.info { "sentry-cli not found in sentry.properties file" }

        // next up try a packaged version of sentry-cli
        val cliSuffix = getCliSuffix()
        project.logger.info { "cliSuffix is $cliSuffix" }

        if (!cliSuffix.isNullOrBlank()) {
            val resourcePath = "/bin/sentry-cli-$cliSuffix"

            // if we are not in a jar, we can use the file directly
            project.logger.info { "Searching for $resourcePath in resources folder..." }

            searchCliInResources(resourcePath)?.let {
                project.logger.info { "cli Found: $it" }
                return@getSentryCliPath it
            } ?: project.logger.info { "Failed to load sentry-cli from resource folder" }

            // otherwise we need to unpack into a file
            project.logger.info { "Trying to load cli from $resourcePath in a temp file..." }

            loadCliFromResourcesToTemp(resourcePath)?.let {
                project.logger.info { "cli Found: $it" }
                return@getSentryCliPath it
            } ?: project.logger.info { "Failed to load sentry-cli from resource folder" }
        }

        project.logger.error { "Falling back to invoking `sentry-cli` from shell" }
        return "sentry-cli"
    }

    internal fun getSentryPropertiesPath(project: Project): String? =
        listOf(
            project.file("sentry.properties"),
            project.rootProject.file("sentry.properties")
        ).firstOrNull(File::exists)?.path

    internal fun searchCliInPropertiesFile(project: Project): String? {
        return getSentryPropertiesPath(project)?.let { propertiesFile ->
            runCatching {
                Properties()
                    .apply { load(FileInputStream(propertiesFile)) }
                    .getProperty("cli.executable")
            }.getOrNull()
        }
    }

    internal fun searchCliInResources(resourcePath: String): String? {
        val resourceURL = javaClass.getResource(resourcePath)
        val resourceFile = resourceURL?.let { File(it.file) }
        return if (resourceFile?.exists() == true) {
            resourceFile.absolutePath
        } else {
            null
        }
    }

    internal fun loadCliFromResourcesToTemp(resourcePath: String): String? {
        val resourceStream = javaClass.getResourceAsStream(resourcePath)
        val tempFile = File.createTempFile(".sentry-cli", ".exe").apply {
            deleteOnExit()
            setExecutable(true)
        }

        return if (resourceStream != null) {
            FileOutputStream(tempFile).use { output ->
                resourceStream.use { input ->
                    input.copyTo(output)
                }
            }
            tempFile.absolutePath
        } else {
            null
        }
    }

    internal fun getCliSuffix(): String? {
        // TODO: change to .lowercase(Locale.ROOT) when using Kotlin 1.6
        val osName = System.getProperty("os.name").toLowerCase(Locale.ROOT)
        val osArch = System.getProperty("os.arch")
        return when {
            "mac" in osName -> "Darwin-universal"
            "linux" in osName -> if (osArch == "amd64") "Linux-x86_64" else "Linux-$osArch"
            "win" in osName -> "Windows-i686.exe"
            else -> null
        }
    }
}
