package com.yizhidao.app

import android.content.Context
import com.yizhidao.ReadingRecord
import com.yizhidao.ReadingRecordCodec
import com.yizhidao.VerificationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ReadingRepository(context: Context) {
    private val file = context.filesDir.resolve("readings.json")
    private val mutex = Mutex()
    private val _records = MutableStateFlow(loadFromDisk())
    val records: StateFlow<List<ReadingRecord>> = _records.asStateFlow()

    private fun loadFromDisk(): List<ReadingRecord> {
        if (!file.exists()) return emptyList()
        return runCatching { ReadingRecordCodec.decodeList(file.readText()) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.createdAtEpochMs }
    }

    suspend fun insert(record: ReadingRecord) = mutate { list ->
        list.removeAll { it.id == record.id }
        list.add(0, record)
    }

    suspend fun update(record: ReadingRecord) = mutate { list ->
        val idx = list.indexOfFirst { it.id == record.id }
        if (idx >= 0) list[idx] = record
    }

    suspend fun updateQuestion(id: String, question: String?) = mutate { list ->
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) list[idx] = list[idx].withQuestion(question)
    }

    suspend fun updateVerification(id: String, status: VerificationStatus, note: String?) =
        mutate { list ->
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) list[idx] = list[idx].withVerification(status, note)
        }

    suspend fun delete(id: String) = mutate { list ->
        list.removeAll { it.id == id }
    }

    private suspend fun mutate(block: (MutableList<ReadingRecord>) -> Unit) {
        mutex.withLock {
            val next = _records.value.toMutableList()
            block(next)
            next.sortByDescending { it.createdAtEpochMs }
            withContext(Dispatchers.IO) {
                file.writeText(ReadingRecordCodec.encodeList(next))
            }
            _records.value = next
        }
    }
}
