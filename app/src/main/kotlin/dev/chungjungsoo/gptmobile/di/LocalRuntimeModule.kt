package dev.chungjungsoo.gptmobile.di

import android.content.Context
import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.data.localruntime.LocalEngineHolder
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntime
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntimeImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalRuntimeModule {
    @Provides
    @Singleton
    fun provideLocalRuntime(
        @ApplicationContext context: Context
    ): LocalRuntime = LocalEngineHolder(LocalRuntimeImpl(context))

    @Provides
    @Singleton
    @DeviceSocModel
    fun provideDeviceSocModel(): String = Build.SOC_MODEL.orEmpty()
}
