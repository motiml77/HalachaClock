package com.zmanimclock.app.di

import android.content.Context
import androidx.room.Room
import com.zmanimclock.app.data.local.ZmanimDatabase
import com.zmanimclock.app.feature.alarms.data.AlarmDao
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
    fun provideDatabase(@ApplicationContext context: Context): ZmanimDatabase =
        Room.databaseBuilder(context, ZmanimDatabase::class.java, "zmanim.db")
            // pre-release: schema still moves; real migrations start at v1.0
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideChaiTablesDao(database: ZmanimDatabase): ChaiTablesDao =
        database.chaiTablesDao()

    @Provides
    fun provideAlarmDao(database: ZmanimDatabase): AlarmDao =
        database.alarmDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
}
