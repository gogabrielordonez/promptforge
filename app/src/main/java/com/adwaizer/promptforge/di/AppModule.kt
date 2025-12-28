package com.adwaizer.promptforge.di

import android.content.Context
import com.adwaizer.promptforge.core.EnhancementEngine
import com.adwaizer.promptforge.core.GemmaInference
import com.adwaizer.promptforge.data.PreferencesManager
import com.adwaizer.promptforge.data.PromptRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGemmaInference(
        @ApplicationContext context: Context
    ): GemmaInference {
        return GemmaInference(context)
    }

    @Provides
    @Singleton
    fun provideEnhancementEngine(
        inference: GemmaInference
    ): EnhancementEngine {
        return EnhancementEngine(inference)
    }

    @Provides
    @Singleton
    fun providePreferencesManager(
        @ApplicationContext context: Context
    ): PreferencesManager {
        return PreferencesManager(context)
    }

    @Provides
    @Singleton
    fun providePromptRepository(
        @ApplicationContext context: Context
    ): PromptRepository {
        return PromptRepository(context)
    }
}
