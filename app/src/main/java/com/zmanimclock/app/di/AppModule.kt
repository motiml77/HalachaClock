package com.zmanimclock.app.di

import android.content.Context
import androidx.room.Room
import com.zmanimclock.app.feature.alerts.data.local.AlertDao
import com.zmanimclock.app.feature.alerts.data.local.AlertDatabase
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
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
        )
            .addMigrations(AlertDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideAlertDao(database: AlertDatabase): AlertDao {
        return database.alertDao()
    }

    @Provides
    fun provideChaiTablesDao(database: AlertDatabase): ChaiTablesDao {
        return database.chaiTablesDao()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
