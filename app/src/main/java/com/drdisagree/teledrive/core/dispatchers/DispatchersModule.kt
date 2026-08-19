package com.drdisagree.teledrive.core.dispatchers

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DispatchersModule {

    @Binds
    @Singleton
    fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider
}
