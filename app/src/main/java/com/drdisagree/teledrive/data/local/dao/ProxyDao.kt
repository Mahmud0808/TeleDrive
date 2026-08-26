package com.drdisagree.teledrive.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drdisagree.teledrive.data.local.entity.ProxyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProxyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(proxy: ProxyEntity)

    @Query("SELECT * FROM proxies ORDER BY addedAt ASC")
    fun observeAll(): Flow<List<ProxyEntity>>

    @Query("SELECT * FROM proxies ORDER BY addedAt ASC")
    suspend fun all(): List<ProxyEntity>

    @Query("SELECT * FROM proxies WHERE id = :id")
    suspend fun byId(id: String): ProxyEntity?

    @Query("DELETE FROM proxies WHERE id = :id")
    suspend fun delete(id: String)
}
