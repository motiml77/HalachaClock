package com.zmanimclock.app.di

import android.content.Context
import androidx.room.Room
import com.zmanimclock.app.feature.alerts.data.local.AlertDao
import com.zmanimclock.app.feature.alerts.data.local.AlertDatabase
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
    fun provideAlertDatabase(@ApplicationContext context: Context): AlertDatabase {
        return Room.databaseBuilder(
            context,
            AlertDatabase::class.java,
            "zmanim_alerts.db"
        ).build()
    }

    @Provides
    fun provideAlertDao(database: AlertDatabase): AlertDao {
        return database.alertDao()
    }
}
