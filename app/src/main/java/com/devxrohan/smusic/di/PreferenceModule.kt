package com.devxrohan.smusic.di

import android.content.Context
import com.devxrohan.smusic.ui.component.NamePreferenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PreferenceModule {

    @Provides
    @Singleton
    fun provideNamePreferenceManager(
        @ApplicationContext context: Context
    ): NamePreferenceManager {
        return NamePreferenceManager(context)
    }
}
