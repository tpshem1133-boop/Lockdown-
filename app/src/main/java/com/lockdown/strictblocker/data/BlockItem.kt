package com.lockdown.strictblocker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BlockType { APP, WEBSITE, KEYWORD }

/**
 * One entry in the user's lists.
 *
 * APP / WEBSITE rows are interpreted through the current mode:
 *  - BLACKLIST mode: the row is blocked.
 *  - WHITELIST mode: the row is allowed, everything else is blocked.
 * KEYWORD rows always block (they only make sense in blacklist web mode).
 */
@Entity(
    tableName = "block_items",
    indices = [Index(value = ["type", "value"], unique = true)]
)
data class BlockItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: BlockType,
    /** package name, normalized domain, or lowercase keyword */
    val value: String,
    /** human-readable label shown in the UI */
    val label: String
)
