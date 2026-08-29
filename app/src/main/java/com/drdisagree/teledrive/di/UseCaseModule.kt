package com.drdisagree.teledrive.di

import com.drdisagree.teledrive.domain.usecase.DecideBackupActionUseCase
import com.drdisagree.teledrive.domain.usecase.EvaluateExclusionsUseCase
import com.drdisagree.teledrive.domain.usecase.TrashExpiryCalculator
import com.drdisagree.teledrive.domain.usecase.ValidateUploadUseCase

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val useCaseModule = module {
    factoryOf(::DecideBackupActionUseCase)
    factoryOf(::EvaluateExclusionsUseCase)
    factoryOf(::TrashExpiryCalculator)
    factoryOf(::ValidateUploadUseCase)
}
