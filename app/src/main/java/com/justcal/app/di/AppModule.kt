package com.justcal.app.di

import android.content.Context
import androidx.room.Room
import com.justcal.app.data.*
import com.justcal.app.domain.DiaryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): DiaryDatabase =
        Room.databaseBuilder(context, DiaryDatabase::class.java, "just-cal.db").build()
    @Provides fun dao(database: DiaryDatabase): DiaryDao = database.diaryDao()
    @Provides @Singleton fun repository(dao: DiaryDao): DiaryRepository = RoomDiaryRepository(dao)
    @Provides fun clock(): Clock = Clock.systemUTC()
}
