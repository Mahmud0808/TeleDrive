package com.drdisagree.teledrive.data.mapper

import com.drdisagree.teledrive.data.local.entity.ExclusionEntity
import com.drdisagree.teledrive.domain.model.Exclusion

fun ExclusionEntity.toDomain(): Exclusion = Exclusion(
    id = id,
    type = type,
    value = value,
    enabled = enabled,
    createdAt = createdAt
)
