package com.justcal.app.domain

import kotlinx.coroutines.flow.Flow

interface DiaryRepository {
    fun observeDay(dayEpoch: Long): Flow<List<DiaryEntry>>
    fun observeAll(): Flow<List<DiaryEntry>>
    suspend fun get(id: Long): DiaryEntry?
    suspend fun save(entry: DiaryEntry): Long
    /** Copy persisted food provenance, never a scan session or an editor draft. */
    suspend fun duplicate(id: Long, dayEpoch: Long, createdAtMillis: Long): Long
    /** Update only the calendar day, preserving all other persisted fields. */
    suspend fun changeDate(id: Long, dayEpoch: Long)
    suspend fun delete(id: Long)
}
