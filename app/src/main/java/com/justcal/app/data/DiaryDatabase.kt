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

@Database(entities = [DiaryEntryEntity::class, ProductEntity::class, ProductRevisionEntity::class,
    EntryProductLinkEntity::class, FoodSearchEntity::class, FoodTokenEntity::class, FoodHistoryMember::class], version = 3, exportSchema = true)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    abstract fun foodCatalogDao(): FoodCatalogDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS products (id TEXT NOT NULL PRIMARY KEY, createdAtMillis INTEGER NOT NULL, archived INTEGER NOT NULL)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS product_revisions (
                    id TEXT NOT NULL PRIMARY KEY, productId TEXT NOT NULL, previousRevisionId TEXT,
                    name TEXT NOT NULL, energy INTEGER NOT NULL, protein INTEGER NOT NULL, fat INTEGER NOT NULL, carbs INTEGER NOT NULL,
                    savedAtMillis INTEGER NOT NULL, brand TEXT, manufacturer TEXT, retailer TEXT, deliveryService TEXT,
                    packageMassHundredths INTEGER, origins TEXT NOT NULL,
                    FOREIGN KEY(productId) REFERENCES products(id) ON DELETE CASCADE,
                    FOREIGN KEY(previousRevisionId) REFERENCES product_revisions(id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED)""")
                db.execSQL("CREATE INDEX index_product_revisions_productId ON product_revisions(productId)")
                db.execSQL("CREATE INDEX index_product_revisions_previousRevisionId ON product_revisions(previousRevisionId)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS entry_product_links (
                    entryId TEXT NOT NULL PRIMARY KEY, revisionId TEXT NOT NULL, provenance TEXT NOT NULL,
                    FOREIGN KEY(entryId) REFERENCES diary_entries(portableId) ON DELETE CASCADE,
                    FOREIGN KEY(revisionId) REFERENCES product_revisions(id) ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX index_entry_product_links_revisionId ON entry_product_links(revisionId)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS food_search (
                    `key` TEXT NOT NULL PRIMARY KEY, source TEXT NOT NULL, normalizedName TEXT NOT NULL,
                    name TEXT NOT NULL, energy INTEGER NOT NULL, protein INTEGER NOT NULL, fat INTEGER NOT NULL,
                    carbs INTEGER NOT NULL, normalizationVersion INTEGER NOT NULL)""")
                listOf("energy", "protein", "fat", "carbs").forEach { column ->
                    db.execSQL("CREATE INDEX index_food_search_$column ON food_search($column)")
                }
                db.execSQL("""CREATE TABLE IF NOT EXISTS food_tokens (
                    token TEXT NOT NULL, ownerKey TEXT NOT NULL, PRIMARY KEY(token, ownerKey),
                    FOREIGN KEY(ownerKey) REFERENCES food_search(`key`) ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX index_food_tokens_ownerKey ON food_tokens(ownerKey)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS food_history_members (
                    entryId TEXT NOT NULL PRIMARY KEY, ownerKey TEXT NOT NULL,
                    FOREIGN KEY(entryId) REFERENCES diary_entries(portableId) ON DELETE CASCADE,
                    FOREIGN KEY(ownerKey) REFERENCES food_search(`key`) ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX index_food_history_members_ownerKey ON food_history_members(ownerKey)")
                installSearchTriggers(db)
            }
        }
        val CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) = installSearchTriggers(db)
            override fun onOpen(db: SupportSQLiteDatabase) = installSearchTriggers(db)
        }
        private fun installSearchTriggers(db: SupportSQLiteDatabase) {
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS food_history_orphan AFTER DELETE ON food_history_members
                BEGIN DELETE FROM food_search WHERE `key` = OLD.ownerKey AND NOT EXISTS
                (SELECT 1 FROM food_history_members m WHERE m.ownerKey = OLD.ownerKey); END""")
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS food_history_changed AFTER UPDATE OF name,
                energyKcalHundredths, proteinGramsHundredths, fatGramsHundredths, carbsGramsHundredths ON diary_entries
                BEGIN DELETE FROM food_history_members WHERE entryId = NEW.portableId; END""")
            db.execSQL("""CREATE TRIGGER IF NOT EXISTS food_history_linked AFTER INSERT ON entry_product_links
                BEGIN DELETE FROM food_history_members WHERE entryId = NEW.entryId; END""")
        }
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
