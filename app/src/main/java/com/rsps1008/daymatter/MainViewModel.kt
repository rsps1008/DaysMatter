package com.rsps1008.daymatter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rsps1008.daymatter.data.DayMatterRepository
import com.rsps1008.daymatter.data.EventItem
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    val events: StateFlow<List<EventItem>> = DayMatterRepository.events

    fun save(event: EventItem) {
        viewModelScope.launch {
            DayMatterRepository.upsert(event)
        }
    }

    fun delete(eventId: Long) {
        viewModelScope.launch {
            DayMatterRepository.delete(eventId)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            DayMatterRepository.clearAll()
        }
    }

    suspend fun importCsv(csv: String, replaceExisting: Boolean = false) =
        DayMatterRepository.importCsv(csv, replaceExisting)

    suspend fun exportCsv(): String = DayMatterRepository.exportCsv()
}
