package com.justcal.app.data

import androidx.room.*
import com.justcal.app.domain.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "diary_entries", indices = [Index("dayEpoch")])
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val energyKcalHundredths: Long,
    val proteinGramsHundredths: Long,
    val fatGramsHundredths: Long,
    val carbsGramsHundredths: Long,
    val eatenGramsHundredths: Long,
    val dayEpoch: Long,
    val createdAtMillis: Long,
    val source: String,
) {
    fun toDomain() = DiaryEntry(id, name,
        NutritionPer100g(energyKcalHundredths, proteinGramsHundredths, fatGramsHundredths, carbsGramsHundredths),
        eatenGramsHundredths, dayEpoch, createdAtMillis, EntrySource.valueOf(source))

    companion object {
        fun from(entry: DiaryEntry) = DiaryEntryEntity(entry.id, entry.name,
            entry.per100g.energyKcalHundredths, entry.per100g.proteinGramsHundredths,
            entry.per100g.fatGramsHundredths, entry.per100g.carbsGramsHundredths,
            entry.eatenGramsHundredths, entry.dayEpoch, entry.createdAtMillis, entry.source.name)
    }
}

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries WHERE dayEpoch = :dayEpoch ORDER BY createdAtMillis DESC, id DESC")
    fun observeDay(dayEpoch: Long): Flow<List<DiaryEntryEntity>>
    @Query("SELECT * FROM diary_entries ORDER BY dayEpoch DESC, createdAtMillis DESC, id DESC")
    fun observeAll(): Flow<List<DiaryEntryEntity>>
    @Query("SELECT * FROM diary_entries WHERE id = :id")
    suspend fun get(id: Long): DiaryEntryEntity?
    @Insert
    suspend fun insert(entry: DiaryEntryEntity): Long
    @Update
    suspend fun update(entry: DiaryEntryEntity): Int
    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [DiaryEntryEntity::class], version = 1, exportSchema = true)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
}
