package com.example.group4app.integration

import com.legenddrry.feature.tools.contracts.AliasesRepository
import com.legenddrry.feature.tools.contracts.EmailAlias
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Persists demo alias records to an app-private JSON file. No mailbox is created and no email is
 * ever sent or forwarded: addresses use the reserved example.com domain, exactly like
 * InMemoryAliasesRepository ("demo.<uuid-without-dashes>@example.com").
 *
 * File format (UTF-8):
 * `{"version":1,"aliases":[{"id","serviceName","aliasAddress","createdAtEpochMs","isActive"}]}`
 * newest first. Loaded synchronously at construction; a missing file yields an empty list, an
 * unreadable/invalid file is moved aside to `<name>.corrupt` (never silently deleted) and the
 * repository starts empty. Writes are atomic (temp file + rename) and mutations are serialized.
 * A mutation only publishes its new list after the file write succeeded (IOException otherwise).
 */
class FileAliasesRepository private constructor(
    private val file: File,
    private val clock: () -> Long,
    private val ioDispatcher: CoroutineDispatcher,
) : AliasesRepository {

    constructor(file: File, clock: () -> Long = System::currentTimeMillis) :
        this(file, clock, Dispatchers.IO)

    /** Deterministic dispatcher seam for cancellation-at-commit regression tests. */
    internal constructor(file: File, ioDispatcher: CoroutineDispatcher, clock: () -> Long = System::currentTimeMillis) :
        this(file, clock, ioDispatcher)

    private val mutex = Mutex()
    private val mutableAliases = MutableStateFlow(loadFromDisk())
    override val aliases: StateFlow<List<EmailAlias>> = mutableAliases.asStateFlow()

    override suspend fun create(serviceName: String): EmailAlias = mutex.withLock {
        val service = serviceName.trim()
        val error = serviceNameError(service)
        require(error == null) { error ?: "Enter a valid service name." }
        val current = mutableAliases.value
        var id: String
        do { id = UUID.randomUUID().toString() } while (current.any { it.id == id })
        val alias = EmailAlias(
            id = id,
            serviceName = service,
            aliasAddress = "demo.${id.replace("-", "")}@example.com",
            createdAtEpochMs = clock(),
            isActive = true,
        )
        commit(listOf(alias) + current)
        alias
    }

    override suspend fun setActive(id: String, active: Boolean) = mutex.withLock {
        val current = mutableAliases.value
        require(current.any { it.id == id }) { "This alias no longer exists." }
        commit(current.map { if (it.id == id) it.copy(isActive = active) else it })
    }

    override suspend fun delete(id: String) = mutex.withLock {
        val current = mutableAliases.value
        require(current.any { it.id == id }) { "This alias no longer exists." }
        commit(current.filterNot { it.id == id })
    }

    /** Must be called with [mutex] held. Persists first, then publishes. */
    private suspend fun commit(updated: List<EmailAlias>) {
        withContext(ioDispatcher) {
            writeAtomically(encode(updated))
            // Publish before crossing the cancellable dispatcher boundary. A caller can leave
            // the screen after the atomic rename; the next mutation must see that durable state.
            mutableAliases.value = updated
        }
    }

    private fun writeAtomically(content: String) {
        val dir = file.absoluteFile.parentFile
        if (dir != null && !dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw IOException("Cannot create directory for alias storage.")
        }
        val tmp = File(dir, file.name + ".tmp")
        try {
            FileOutputStream(tmp).use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun loadFromDisk(): List<EmailAlias> {
        if (!file.exists()) return emptyList()
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (_: IOException) {
            return emptyList()
        }
        return try {
            decode(text)
        } catch (_: JSONException) {
            preserveCorruptFile(); emptyList()
        } catch (_: IllegalArgumentException) {
            preserveCorruptFile(); emptyList()
        }
    }

    private fun preserveCorruptFile() {
        val dir = file.absoluteFile.parentFile
        var target = File(dir, file.name + CORRUPT_SUFFIX)
        if (target.exists()) target = File(dir, file.name + CORRUPT_SUFFIX + "." + clock())
        try {
            Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: IOException) {
            try { file.copyTo(target, overwrite = true) } catch (_: IOException) { /* best effort */ }
        }
    }

    internal companion object {
        const val FORMAT_VERSION = 1
        const val CORRUPT_SUFFIX = ".corrupt"

        /** Mirrors the tools module's (internal) alias service-name validation. */
        fun serviceNameError(serviceName: String): String? = when {
            serviceName.trim().isEmpty() -> "Enter a website or service name."
            serviceName.trim().length > 120 -> "Use 120 characters or fewer."
            serviceName.any { it.isISOControl() } -> "Use a single-line website or service name."
            else -> null
        }

        fun encode(aliases: List<EmailAlias>): String {
            val array = JSONArray()
            aliases.forEach { alias ->
                array.put(
                    JSONObject()
                        .put("id", alias.id)
                        .put("serviceName", alias.serviceName)
                        .put("aliasAddress", alias.aliasAddress)
                        .put("createdAtEpochMs", alias.createdAtEpochMs)
                        .put("isActive", alias.isActive)
                )
            }
            return JSONObject().put("version", FORMAT_VERSION).put("aliases", array).toString()
        }

        /** Throws JSONException / IllegalArgumentException for any invalid content. */
        fun decode(text: String): List<EmailAlias> {
            val root = JSONObject(text)
            val array = root.getJSONArray("aliases")
            val result = ArrayList<EmailAlias>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val alias = EmailAlias(
                    id = obj.getString("id"),
                    serviceName = obj.getString("serviceName"),
                    aliasAddress = obj.getString("aliasAddress"),
                    createdAtEpochMs = obj.getLong("createdAtEpochMs"),
                    isActive = obj.getBoolean("isActive"),
                )
                require(alias.id.isNotBlank()) { "Blank alias id." }
                require(alias.aliasAddress.isNotBlank()) { "Blank alias address." }
                result += alias
            }
            require(result.map { it.id }.distinct().size == result.size) { "Duplicate alias ids." }
            return result
        }
    }
}
