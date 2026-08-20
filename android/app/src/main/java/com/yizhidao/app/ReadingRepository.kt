package com.yizhidao.app

import android.content.Context
import com.yizhidao.HexagramStore
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

@Serializable
data class HistoryTrashEntry(
    val id: String,
    val deletedAtEpochMs: Long,
    val record: ReadingRecord,
)

class ReadingRepository(context: Context) {
    private val file = context.filesDir.resolve("readings.json")
    private val trashFile = context.filesDir.resolve("history-trash.json")
    private val mutex = Mutex()
    private val _records = MutableStateFlow(loadFromDisk())
    private val _trash = MutableStateFlow(loadTrash())
    val records: StateFlow<List<ReadingRecord>> = _records.asStateFlow()
    val trash: StateFlow<List<HistoryTrashEntry>> = _trash.asStateFlow()

    private fun loadFromDisk(): List<ReadingRecord> {
        if (!file.exists()) return emptyList()
        return runCatching { ReadingRecordCodec.decodeList(file.readText()) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.createdAtEpochMs }
    }

    private fun loadTrash(): List<HistoryTrashEntry> {
        if (!trashFile.exists()) return emptyList()
        return runCatching {
            HexagramStore.json.decodeFromString(
                ListSerializer(HistoryTrashEntry.serializer()),
                trashFile.readText(),
            )
        }.getOrDefault(emptyList()).sortedByDescending { it.deletedAtEpochMs }
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

    /** 删除进回收站，与 iOS `HistoryTrashStore.archive` 一致。 */
    suspend fun archive(id: String) {
        mutex.withLock {
            val next = _records.value.toMutableList()
            val rec = next.find { it.id == id } ?: return@withLock
            next.removeAll { it.id == id }
            val trashNext = _trash.value.toMutableList()
            trashNext.add(
                0,
                HistoryTrashEntry(
                    id = UUID.randomUUID().toString(),
                    deletedAtEpochMs = System.currentTimeMillis(),
                    record = rec,
                ),
            )
            persist(next, trashNext)
        }
    }

    suspend fun restoreTrash(entryId: String) {
        mutex.withLock {
            val trashNext = _trash.value.toMutableList()
            val entry = trashNext.find { it.id == entryId } ?: return@withLock
            trashNext.removeAll { it.id == entryId }
            val next = _records.value.toMutableList()
            next.removeAll { it.id == entry.record.id }
            next.add(0, entry.record)
            persist(next, trashNext)
        }
    }

    suspend fun removeTrash(entryId: String) {
        mutex.withLock {
            val trashNext = _trash.value.toMutableList()
            trashNext.removeAll { it.id == entryId }
            persist(_records.value, trashNext)
        }
    }

    suspend fun clearTrash() {
        mutex.withLock {
            persist(_records.value, emptyList())
        }
    }

    private suspend fun mutate(block: (MutableList<ReadingRecord>) -> Unit) {
        mutex.withLock {
            val next = _records.value.toMutableList()
            block(next)
            persist(next, _trash.value)
        }
    }

    private suspend fun persist(records: List<ReadingRecord>, trash: List<HistoryTrashEntry>) {
        val sortedRecords = records.sortedByDescending { it.createdAtEpochMs }
        val sortedTrash = trash.sortedByDescending { it.deletedAtEpochMs }
        withContext(Dispatchers.IO) {
            file.writeText(ReadingRecordCodec.encodeList(sortedRecords))
            trashFile.writeText(
                HexagramStore.json.encodeToString(
                    ListSerializer(HistoryTrashEntry.serializer()),
                    sortedTrash,
                ),
            )
        }
        _records.value = sortedRecords
        _trash.value = sortedTrash
    }
}
