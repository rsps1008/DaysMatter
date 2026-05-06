package com.rsps1008.daymatter.data

import android.content.Context
import android.util.Log
import com.rsps1008.daymatter.alarm.NotificationScheduler
import com.rsps1008.daymatter.widget.DayMatterWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

object DayMatterRepository {
    private const val TAG = "DayMatterRepository"
    private const val STORAGE_FILE = "daymatter_events.json"

    private lateinit var appContext: Context
    private val initialized = AtomicBoolean(false)
    private val _events = MutableStateFlow<List<EventItem>>(emptyList())

    val events: StateFlow<List<EventItem>> = _events.asStateFlow()

    fun initialize(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            appContext = context.applicationContext
            _events.value = readEvents()
        }
    }

    fun currentEvents(): List<EventItem> = _events.value

    fun sortEvents(events: List<EventItem>): List<EventItem> {
        return events.sortedWith(eventComparator())
    }

    fun findById(id: Long): EventItem? = _events.value.firstOrNull { it.id == id }

    suspend fun upsert(event: EventItem) {
        withContext(Dispatchers.IO) {
            val normalized = synchronized(this@DayMatterRepository) {
                val list = _events.value.toMutableList()
                val normalizedEvent = if (event.id == 0L) {
                    event.copy(
                        id = nextId(list),
                        createTime = if (event.createTime == 0L) System.currentTimeMillis() else event.createTime,
                    )
                } else {
                    event
                }
                val index = list.indexOfFirst { it.id == normalizedEvent.id }
                if (index >= 0) {
                    list[index] = normalizedEvent
                } else {
                    list.add(normalizedEvent)
                }
                _events.value = list.sortedWith(eventComparator())
                normalizedEvent
            }
            val updated = _events.value
            persist(updated)
            NotificationScheduler.syncAll(appContext, updated)
            if (normalized.showInWidget && DayMatterWidgetProvider.isNearestWidgetEvent(appContext, normalized.id)) {
                DayMatterWidgetProvider.pinEventToWidget(appContext, normalized.id)
            } else {
                DayMatterWidgetProvider.requestUpdate(appContext)
            }
        }
    }

    suspend fun delete(id: Long) {
        withContext(Dispatchers.IO) {
            val updated = synchronized(this@DayMatterRepository) {
                _events.value.filterNot { it.id == id }.sortedWith(eventComparator())
            }
            _events.value = updated
            persist(updated)
            NotificationScheduler.cancel(appContext, id)
            DayMatterWidgetProvider.requestUpdate(appContext)
        }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            val previous = _events.value
            _events.value = emptyList()
            persist(emptyList())
            previous.forEach { NotificationScheduler.cancel(appContext, it.id) }
            DayMatterWidgetProvider.requestUpdate(appContext)
        }
    }

    suspend fun replaceAll(newEvents: List<EventItem>) {
        withContext(Dispatchers.IO) {
            val normalized = newEvents
                .mapIndexed { index, event ->
                    event.copy(
                        id = if (event.id == 0L) nextId(_events.value + newEvents.take(index)) else event.id,
                        createTime = if (event.createTime == 0L) System.currentTimeMillis() else event.createTime,
                    )
                }
                .sortedWith(eventComparator())

            _events.value = normalized
            persist(normalized)
            NotificationScheduler.syncAll(appContext, normalized)
            DayMatterWidgetProvider.requestUpdate(appContext)
        }
    }

    suspend fun importCsv(csv: String, replaceExisting: Boolean = false): CsvImportResult {
        return withContext(Dispatchers.IO) {
            val result = CsvCodec.decode(csv)
            if (result.imported.isNotEmpty()) {
                val merged = if (replaceExisting) mutableListOf() else _events.value.toMutableList()
                var nextId = nextId(merged)
                result.imported.forEach { imported ->
                    merged.add(imported.copy(id = nextId++))
                }
                val sorted = sortEvents(merged)
                _events.value = sorted
                persist(sorted)
                NotificationScheduler.syncAll(appContext, sorted)
                DayMatterWidgetProvider.requestUpdate(appContext)
            }
            result
        }
    }

    suspend fun exportCsv(): String {
        return withContext(Dispatchers.IO) {
            CsvCodec.encode(sortEvents(_events.value))
        }
    }

    private fun readEvents(): List<EventItem> {
        val file = storageFile()
        if (!file.exists()) return emptyList()
        return runCatching {
            val text = file.readText()
            if (text.isBlank()) return emptyList()
            val array = JSONArray(text)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(item.toEventItem())
                }
            }.let(::sortEvents)
        }.onFailure {
            Log.e(TAG, "Failed to load events", it)
        }.getOrElse { emptyList() }
    }

    private fun persist(events: List<EventItem>) {
        runCatching {
            storageFile().writeText(eventsToJson(events).toString())
        }.onFailure {
            Log.e(TAG, "Failed to persist events", it)
        }
    }

    private fun storageFile(): File {
        return File(appContext.filesDir, STORAGE_FILE)
    }

    private fun eventsToJson(events: List<EventItem>): JSONArray {
        return JSONArray().apply {
            events.forEach { put(it.toJson()) }
        }
    }

    private fun EventItem.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("category", category.name)
            put("date", date.toString())
            put("repeatType", repeatType.name)
            put("repeatInterval", repeatInterval)
            put("enableReminder", enableReminder)
            put("reminderTime", reminderTime?.toString())
            put("showInWidget", showInWidget)
            put("createTime", createTime)
        }
    }

    private fun JSONObject.toEventItem(): EventItem {
        return EventItem(
            id = optLong("id", 0L),
            title = optString("title", ""),
            category = runCatching { EventCategory.valueOf(optString("category", EventCategory.OTHER.name)) }.getOrDefault(EventCategory.OTHER),
            date = runCatching { LocalDate.parse(optString("date")) }.getOrElse { LocalDate.now() },
            repeatType = runCatching { RepeatType.valueOf(optString("repeatType", RepeatType.NONE.name)) }.getOrDefault(RepeatType.NONE),
            repeatInterval = optInt("repeatInterval", 0),
            enableReminder = optBoolean("enableReminder", false),
            reminderTime = if (has("reminderTime") && !isNull("reminderTime")) {
                optString("reminderTime").takeIf { it.isNotBlank() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            } else {
                null
            },
            showInWidget = optBoolean("showInWidget", false),
            createTime = optLong("createTime", System.currentTimeMillis()),
        )
    }

    private fun eventComparator(): Comparator<EventItem> {
        return compareBy<EventItem> { CountdownLogic.resolveCountdown(it).days < 0 }
            .thenBy { event ->
                val days = CountdownLogic.resolveCountdown(event).days
                if (days < 0) abs(days) else days
            }
            .thenBy { it.title }
            .thenByDescending { it.createTime }
    }

    private fun nextId(events: List<EventItem>): Long {
        return (events.maxOfOrNull { it.id } ?: 0L) + 1L
    }
}
