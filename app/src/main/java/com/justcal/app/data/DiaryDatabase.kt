package com.justcal.app.data

import androidx.room.*
import com.justcal.app.domain.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

@Entity(tableName = "diary_entries", indices = [Index("dayEpoch"), Index(value = ["portableId"], unique = true)])
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
    @ColumnInfo(defaultValue = "''") val portableId: String = UUID.randomUUID().toString(),
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
    @Query("SELECT * FROM diary_entries ORDER BY dayEpoch, createdAtMillis, id")
    suspend fun getAll(): List<DiaryEntryEntity>
    @Query("DELETE FROM diary_entries")
    suspend fun deleteAll()
    @Insert
    suspend fun insert(entry: DiaryEntryEntity): Long
    @Transaction
    suspend fun duplicate(id: Long, dayEpoch: Long, createdAtMillis: Long): Long {
        val original = checkNotNull(get(id)) { "The entry no longer exists" }
        return insert(original.copy(id = 0, portableId = UUID.randomUUID().toString(),
            dayEpoch = dayEpoch, createdAtMillis = createdAtMillis))
    }
    @Update
    suspend fun update(entry: DiaryEntryEntity): Int
    @Query("UPDATE diary_entries SET dayEpoch = :dayEpoch WHERE id = :id")
    suspend fun changeDate(id: Long, dayEpoch: Long): Int
    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [DiaryEntryEntity::class], version = 2, exportSchema = true)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN portableId TEXT NOT NULL DEFAULT ''")
                // Random UUIDs are generated once in-place; all existing rows and local IDs survive.
                db.execSQL("""UPDATE diary_entries SET portableId =
                    lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' ||
                    substr(lower(hex(randomblob(2))), 2) || '-a' ||
                    substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6)))""")
                db.execSQL("CREATE UNIQUE INDEX index_diary_entries_portableId ON diary_entries(portableId)")
            }
        }
    }
}
