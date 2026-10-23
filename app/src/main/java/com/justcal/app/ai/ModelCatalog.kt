package com.justcal.app.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class ModelCatalog @Inject constructor(@ApplicationContext context: Context) {
    val models: List<ModelDescriptor> = context.assets.open("ai_models.json").bufferedReader().use {
        Json.decodeFromString<List<ModelDescriptor>>(it.readText())
    }.also { entries ->
        require(entries.map { it.id }.distinct().size == entries.size)
        entries.forEach {
            require(it.schemaVersion == 1 && it.revision.matches(Regex("[0-9a-f]{40}")))
            require(it.sha256.matches(Regex("[0-9a-f]{64}")) && it.bytes in 1..MAX_MODEL_BYTES)
            require(it.repository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")))
            require(it.filename.matches(Regex("[A-Za-z0-9_.-]+\\.litertlm")))
        }
    }
    fun matching(hash: String) = models.firstOrNull { it.sha256 == hash }
}

const val MAX_MODEL_BYTES = 16L * 1024 * 1024 * 1024
const val MODEL_STORAGE_RESERVE = 512L * 1024 * 1024
