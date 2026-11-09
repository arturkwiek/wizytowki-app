package com.wizytowki.scan

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Scan in progress, persisted so an interrupted scan can be resumed (UI-09, WF-25). */
interface DraftStore {
    /** Current draft with content, or null. Up to date after [load]. */
    val draft: StateFlow<ScanSession?>

    suspend fun load(): ScanSession?

    /** Copies a scanned image into app-internal storage and returns its file URI. */
    suspend fun importImage(sourceUri: String, prefix: String): String

    /** Saves the session and deletes draft images it no longer references. */
    suspend fun save(session: ScanSession)

    suspend fun clear()
}

@Singleton
class FileDraftStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : DraftStore {
    private val dir get() = File(context.filesDir, "scan_draft")
    private val stateFile get() = File(dir, "draft.txt")
    private val mutex = Mutex()
    private val _draft = MutableStateFlow<ScanSession?>(null)
    override val draft: StateFlow<ScanSession?> = _draft.asStateFlow()

    override suspend fun load(): ScanSession? = io {
        val session = stateFile.takeIf { it.exists() }
            ?.let { runCatching { DraftCodec.decode(it.readText()) }.getOrNull() }
            ?.takeIf { it.referencedFilesExist() }
            ?.takeIf { it.hasContent }
        _draft.value = session
        session
    }

    override suspend fun importImage(sourceUri: String, prefix: String): String = io {
        dir.mkdirs()
        val target = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        val input = context.contentResolver.openInputStream(Uri.parse(sourceUri))
            ?: throw IOException("Cannot open $sourceUri")
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        Uri.fromFile(target).toString()
    }

    override suspend fun save(session: ScanSession) = io {
        dir.mkdirs()
        // Write to a temp file and rename, so a crash mid-write never leaves a corrupt draft.
        val tmp = File(dir, "draft.txt.tmp")
        tmp.writeText(DraftCodec.encode(session))
        if (!tmp.renameTo(stateFile)) {
            stateFile.delete()
            tmp.renameTo(stateFile)
        }
        deleteUnreferenced(session)
        _draft.value = session.takeIf { it.hasContent }
    }

    override suspend fun clear() = io {
        dir.deleteRecursively()
        _draft.value = null
    }

    private fun deleteUnreferenced(session: ScanSession) {
        val keep = setOfNotNull(session.frontUri, session.backUri).mapNotNull { Uri.parse(it).path }.toSet()
        dir.listFiles { file -> file.name.endsWith(".jpg") }?.forEach { if (it.path !in keep) it.delete() }
    }

    private fun ScanSession.referencedFilesExist(): Boolean =
        listOfNotNull(frontUri, backUri).all { uri -> Uri.parse(uri).path?.let { File(it).exists() } == true }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }
}
