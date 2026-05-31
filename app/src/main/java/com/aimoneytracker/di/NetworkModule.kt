package com.aimoneytracker.di

import com.aimoneytracker.data.remote.OpenAiService
import com.aimoneytracker.domain.ai.AiService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    // Swap this binding to AnthropicAiService to use Claude instead of OpenAI.
    @Binds
    @Singleton
    abstract fun bindAiService(impl: OpenAiService): AiService
}
