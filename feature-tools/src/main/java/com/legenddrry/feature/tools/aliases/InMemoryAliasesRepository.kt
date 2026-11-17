package com.legenddrry.feature.tools.aliases

import com.legenddrry.feature.tools.contracts.AliasesRepository
import com.legenddrry.feature.tools.contracts.EmailAlias
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Session-only demonstration records. No mailboxes or email forwarding are created. */
class InMemoryAliasesRepository(
    initialAliases: List<EmailAlias> = emptyList(),
    private val clock: () -> Long = System::currentTimeMillis,
) : AliasesRepository {
    init {
        require(initialAliases.map { it.id }.distinct().size == initialAliases.size) {
            "Alias record IDs must be unique."
        }
        require(initialAliases.all { it.id.isNotBlank() }) { "Alias IDs must not be blank." }
    }

    private val mutex = Mutex()
    private val mutableAliases = MutableStateFlow(initialAliases.toList())
    override val aliases: StateFlow<List<EmailAlias>> = mutableAliases.asStateFlow()

    override suspend fun create(serviceName: String): EmailAlias = mutex.withLock {
        val service = serviceName.trim()
        require(aliasServiceError(service) == null) {
            aliasServiceError(service) ?: "Enter a valid service name."
        }
        // UUID IDs are separate from service names, so duplicate service names are safe.
        var id: String
        do { id = UUID.randomUUID().toString() }
        while (mutableAliases.value.any { it.id == id })
        val alias = EmailAlias(
            id = id,
            serviceName = service,
            aliasAddress = "demo.${id.replace("-", "")}@example.com",
            createdAtEpochMs = clock(),
            isActive = true,
        )
        mutableAliases.value = listOf(alias) + mutableAliases.value
        alias
    }

    override suspend fun setActive(id: String, active: Boolean) = mutex.withLock {
        require(mutableAliases.value.any { it.id == id }) { "This alias no longer exists." }
        mutableAliases.value = mutableAliases.value.map {
            if (it.id == id) it.copy(isActive = active) else it
        }
    }

    override suspend fun delete(id: String) = mutex.withLock {
        require(mutableAliases.value.any { it.id == id }) { "This alias no longer exists." }
        mutableAliases.value = mutableAliases.value.filterNot { it.id == id }
    }
}

internal fun aliasServiceError(serviceName: String): String? = when {
    serviceName.trim().isEmpty() -> "Enter a website or service name."
    serviceName.trim().length > 120 -> "Use 120 characters or fewer."
    serviceName.any { it.isISOControl() } -> "Use a single-line website or service name."
    else -> null
}

/** Search matches either field and remains independent from repository persistence. */
internal fun filterAliases(aliases: List<EmailAlias>, query: String): List<EmailAlias> {
    val text = query.trim()
    return if (text.isEmpty()) aliases else aliases.filter {
        it.serviceName.contains(text, ignoreCase = true) ||
            it.aliasAddress.contains(text, ignoreCase = true)
    }
}
