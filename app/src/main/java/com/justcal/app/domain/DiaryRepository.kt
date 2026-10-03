package com.justcal.app.domain

import kotlinx.coroutines.flow.Flow

interface DiaryRepository {
    fun observeDay(dayEpoch: Long): Flow<List<DiaryEntry>>
    suspend fun get(id: Long): DiaryEntry?
    suspend fun save(entry: DiaryEntry): Long
    suspend fun delete(id: Long)
}
