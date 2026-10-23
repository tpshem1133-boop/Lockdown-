package com.lockdown.strictblocker.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Single source of truth for the block lists.
 *
 * Keeps a volatile in-memory snapshot so the Accessibility Service, the
 * UsageStats monitor and the VPN can make blocking decisions synchronously
 * on hot paths without touching the database.
 */
class BlockRepository private constructor(context: Context) {

    private val dao = LockdownDatabase.get(context).blockDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var snapshot: List<BlockItem> = emptyList()
        private set

    val items: Flow<List<BlockItem>> = dao.observeAll()

    init {
        scope.launch {
            dao.observeAll().collect { snapshot = it }
        }
    }

    suspend fun add(type: BlockType, value: String, label: String) {
        val normalized = when (type) {
            BlockType.APP -> value.trim()
            BlockType.WEBSITE -> normalizeDomain(value)
            BlockType.KEYWORD -> value.trim().lowercase()
        }
        if (normalized.isEmpty()) return
        dao.insert(BlockItem(type = type, value = normalized, label = label.trim().ifEmpty { normalized }))
    }

    suspend fun remove(item: BlockItem) = dao.delete(item)

    suspend fun isListed(type: BlockType, value: String): Boolean =
        dao.count(type, value) > 0

    companion object {
        @Volatile
        private var instance: BlockRepository? = null

        fun get(context: Context): BlockRepository =
            instance ?: synchronized(this) {
                instance ?: BlockRepository(context.applicationContext).also { instance = it }
            }

        /** "HTTPS://www.YouTube.com/watch" -> "youtube.com" */
        fun normalizeDomain(raw: String): String {
            var d = raw.trim().lowercase()
            d = d.removePrefix("https://").removePrefix("http://")
            d = d.substringBefore('/').substringBefore('?').substringBefore('#')
            d = d.substringBefore(':')
            if (d.startsWith("www.")) d = d.removePrefix("www.")
            return d.trim().trimEnd('.')
        }

        /** Extract the host from a URL or bare domain string, or null. */
        fun hostOf(url: String): String? {
            val d = normalizeDomain(url)
            return d.ifEmpty { null }
        }
    }
}
