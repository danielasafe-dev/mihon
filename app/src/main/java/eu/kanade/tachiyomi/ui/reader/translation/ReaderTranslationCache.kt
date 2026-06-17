package eu.kanade.tachiyomi.ui.reader.translation

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

class ReaderTranslationCache(
    app: Application,
    private val json: Json,
) {
    private val cacheDir = File(app.cacheDir, "reader_translation_cache")

    suspend fun get(key: ReaderTranslationPageKey): ReaderTranslationResult? = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        if (!file.exists()) return@withContext null

        runCatching {
            json.decodeFromString<ReaderTranslationResult>(file.readText())
        }.getOrNull()
    }

    suspend fun put(key: ReaderTranslationPageKey, result: ReaderTranslationResult) = withContext(Dispatchers.IO) {
        cacheDir.mkdirs()
        fileFor(key).writeText(json.encodeToString(result))
    }

    private fun fileFor(key: ReaderTranslationPageKey): File {
        return File(cacheDir, "${key.raw.sha256()}.json")
    }
}

private fun String.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())

    return digest.joinToString(separator = "") { "%02x".format(it) }
}
