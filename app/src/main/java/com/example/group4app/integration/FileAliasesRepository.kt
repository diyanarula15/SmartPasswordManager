// LOCKED API (owner: adapters worker): public constructor (file: File, clock = System::currentTimeMillis). SKELETON STUB.
package com.example.group4app.integration

import com.legenddrry.feature.tools.aliases.InMemoryAliasesRepository
import com.legenddrry.feature.tools.contracts.AliasesRepository
import java.io.File

/** Persists demo alias records to an app-private JSON file. No email is ever sent or forwarded. */
class FileAliasesRepository(
    file: File,
    clock: () -> Long = System::currentTimeMillis,
) : AliasesRepository by InMemoryAliasesRepository()
