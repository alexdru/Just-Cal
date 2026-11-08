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
        Room.databaseBuilder(context, DiaryDatabase::class.java, "just-cal.db")
            .addMigrations(DiaryDatabase.MIGRATION_1_2, DiaryDatabase.MIGRATION_2_3)
            .addCallback(DiaryDatabase.CALLBACK).build()
    @Provides fun dao(database: DiaryDatabase): DiaryDao = database.diaryDao()
    @Provides @Singleton fun repository(dao: DiaryDao, database: DiaryDatabase): DiaryRepository = RoomDiaryRepository(dao, database)
    @Provides @Singleton fun catalog(repository: RoomFoodCatalogRepository): com.justcal.app.domain.FoodCatalogRepository = repository
    @Provides fun packageOcr(engine: com.justcal.app.ocr.PaddlePackageOcrEngine): com.justcal.app.ocr.PackageOcrEngine = engine
    @Provides fun updates(repository: GitHubUpdateRepository): com.justcal.app.domain.AppUpdateRepository = repository
    @Provides fun clock(): Clock = Clock.systemUTC()
}
