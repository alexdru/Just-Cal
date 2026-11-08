package com.justcal.app.data

import androidx.room.withTransaction
import com.justcal.app.domain.DiaryEntry
import com.justcal.app.domain.DiaryRepository
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomDiaryRepository @Inject constructor(private val dao: DiaryDao, private val database: DiaryDatabase? = null) : DiaryRepository {
    override fun observeDay(dayEpoch: Long) = dao.observeDay(dayEpoch).map { rows -> rows.map { it.toDomain() } }
    override fun observeAll() = dao.observeAll().map { rows -> rows.map { it.toDomain() } }
    override suspend fun get(id: Long) = dao.get(id)?.toDomain()
    override suspend fun save(entry: DiaryEntry): Long = transaction {
        val id = saveRow(entry)
        index(id)
        id
    }
    private suspend fun saveRow(entry: DiaryEntry): Long {
        val entity = DiaryEntryEntity.from(entry)
        if (entry.id == 0L) return dao.insert(entity)
        val original = checkNotNull(dao.get(entry.id)) { "The entry no longer exists" }
        check(dao.update(entity.copy(portableId = original.portableId)) == 1) { "The entry no longer exists" }
        return entry.id
    }
    override suspend fun duplicate(id: Long, dayEpoch: Long, createdAtMillis: Long): Long =
        transaction { dao.duplicate(id, dayEpoch, createdAtMillis).also { index(it) } }
    override suspend fun changeDate(id: Long, dayEpoch: Long) {
        check(dao.changeDate(id, dayEpoch) == 1) { "The entry no longer exists" }
    }
    override suspend fun delete(id: Long) = dao.delete(id)
    private suspend fun index(id: Long) {
        val db = database ?: return
        val row = dao.get(id) ?: return
        if (db.foodCatalogDao().entryLink(row.portableId) == null) RoomFoodCatalogRepository(db).indexDiary(row)
    }
    private suspend fun <T> transaction(block: suspend () -> T): T = database?.withTransaction { block() } ?: block()
}
