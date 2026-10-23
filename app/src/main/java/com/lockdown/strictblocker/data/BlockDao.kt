package com.lockdown.strictblocker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockDao {
    @Query("SELECT * FROM block_items ORDER BY type ASC, label COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<BlockItem>>

    @Query("SELECT * FROM block_items")
    suspend fun getAll(): List<BlockItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BlockItem): Long

    @Delete
    suspend fun delete(item: BlockItem)

    @Query("DELETE FROM block_items WHERE type = :type AND value = :value")
    suspend fun deleteByValue(type: BlockType, value: String)

    @Query("SELECT COUNT(*) FROM block_items WHERE type = :type AND value = :value")
    suspend fun count(type: BlockType, value: String): Int
}
