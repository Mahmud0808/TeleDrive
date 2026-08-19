package com.drdisagree.teledrive.data.repository

import com.drdisagree.teledrive.data.local.dao.ExclusionDao
import com.drdisagree.teledrive.data.local.entity.ExclusionEntity
import com.drdisagree.teledrive.data.mapper.toDomain
import com.drdisagree.teledrive.domain.model.Exclusion
import com.drdisagree.teledrive.domain.model.ExclusionType
import com.drdisagree.teledrive.domain.repository.ExclusionRepository
import java.util.UUID
import com.drdisagree.teledrive.core.common.SafeLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ExclusionRepositoryImpl @Inject constructor(
    private val exclusionDao: ExclusionDao,
    private val activeChannel: ActiveChannel
) : ExclusionRepository {

    override fun observeAll(): Flow<List<Exclusion>> =
        activeChannel.observe().flatMapLatest { chatId ->
            exclusionDao.observeAll(chatId).map { list -> list.map { it.toDomain() } }
        }

    override suspend fun ensureDefaults(chatId: Long) {
        if (exclusionDao.countByType(chatId, ExclusionType.HIDDEN) > 0) return
        exclusionDao.upsert(
            ExclusionEntity(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                type = ExclusionType.HIDDEN,
                value = HIDDEN_PREFIX,
                enabled = true,
                createdAt = System.currentTimeMillis()
            )
        )
        SafeLog.d(TAG, "Seeded the default hidden-file exclusion")
    }

    override suspend fun getEnabled(): List<Exclusion> =
        exclusionDao.enabled(activeChannel.id()).map { it.toDomain() }

    override suspend fun add(type: ExclusionType, value: String): Exclusion {
        val entity = ExclusionEntity(
            id = UUID.randomUUID().toString(),
            chatId = activeChannel.id(),
            type = type,
            value = value.trim(),
            enabled = true,
            createdAt = System.currentTimeMillis()
        )
        exclusionDao.upsert(entity)
        return entity.toDomain()
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) =
        exclusionDao.setEnabled(id, enabled)

    override suspend fun remove(id: String) = exclusionDao.delete(id)

    private companion object {
        const val TAG = "ExclusionRepository"
        const val HIDDEN_PREFIX = "."
    }
}
