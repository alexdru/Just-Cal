package com.justcal.app.data

import com.justcal.app.domain.DiaryEntry
import com.justcal.app.domain.DiaryRepository
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomDiaryRepository @Inject constructor(private val dao: DiaryDao) : DiaryRepository {
    override fun observeDay(dayEpoch: Long) = dao.observeDay(dayEpoch).map { rows -> rows.map { it.toDomain() } }
    override fun observeAll() = dao.observeAll().map { rows -> rows.map { it.toDomain() } }
    override suspend fun get(id: Long) = dao.get(id)?.toDomain()
    override suspend fun save(entry: DiaryEntry): Long {
        val entity = DiaryEntryEntity.from(entry)
        if (entry.id == 0L) return dao.insert(entity)
        check(dao.update(entity) == 1) { "The entry no longer exists" }
        return entry.id
    }
    override suspend fun delete(id: Long) = dao.delete(id)
}
