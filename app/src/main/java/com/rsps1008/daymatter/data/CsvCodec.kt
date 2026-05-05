package com.rsps1008.daymatter.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CsvImportResult(
    val imported: List<EventItem>,
    val skippedLines: List<String>,
)

object CsvCodec {
    private const val UTF8_BOM = "\uFEFF"
    private val legacyDateFormatter = DateTimeFormatter.ofPattern("yyyy/M/d")
    private val header = listOf(
        "title",
        "category",
        "date",
        "repeatType",
        "repeatInterval",
        "enableReminder",
        "reminderTime",
        "showInWidget",
    )

    fun encode(events: List<EventItem>): String {
        val lines = buildList {
            add(header.joinToString(","))
            events.forEach { event ->
                add(
                    listOf(
                        event.title,
                        event.category.name,
                        event.date.toString(),
                        event.repeatType.name,
                        if (event.repeatInterval > 0) event.repeatInterval.toString() else "",
                        event.enableReminder.toString(),
                        event.reminderTime?.toString().orEmpty(),
                        event.showInWidget.toString(),
                    ).joinToString(",") { escape(it) }
                )
            }
        }
        return UTF8_BOM + lines.joinToString("\n")
    }

    fun decode(csv: String): CsvImportResult {
        val rows = csv
            .removePrefix(UTF8_BOM)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .filter { it.isNotBlank() }
            .toList()

        if (rows.isEmpty()) {
            return CsvImportResult(emptyList(), listOf("CSV is empty"))
        }

        val dataRows = rows.drop(1)
        val imported = mutableListOf<EventItem>()
        val skipped = mutableListOf<String>()

        dataRows.forEachIndexed { index, row ->
            val columns = parseRow(row)
            if (columns.size < header.size) {
                skipped += "line ${index + 2}: not enough columns"
                return@forEachIndexed
            }

            val title = columns[0].trim()
            val category = runCatching { EventCategory.valueOf(columns[1].trim().uppercase(Locale.US)) }.getOrNull()
            val date = parseDate(columns[2].trim())
            val repeatType = runCatching { RepeatType.valueOf(columns[3].trim().uppercase(Locale.US)) }.getOrNull()
            val repeatInterval = columns[4].trim().toIntOrNull() ?: 0
            val enableReminder = columns[5].trim().equals("true", ignoreCase = true)
            val reminderTime = columns[6].trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            val showInWidget = columns[7].trim().equals("true", ignoreCase = true)

            if (title.isBlank() || category == null || date == null || repeatType == null) {
                skipped += "line ${index + 2}: missing required fields"
                return@forEachIndexed
            }

            if (repeatType == RepeatType.CUSTOM && repeatInterval <= 0) {
                skipped += "line ${index + 2}: custom repeat interval must be positive"
                return@forEachIndexed
            }

            imported += EventItem(
                title = title,
                category = category,
                date = date,
                repeatType = repeatType,
                repeatInterval = repeatInterval,
                enableReminder = enableReminder,
                reminderTime = reminderTime,
                showInWidget = showInWidget,
            )
        }

        return CsvImportResult(imported, skipped)
    }

    private fun escape(text: String): String {
        if (text.contains(',') || text.contains('"') || text.contains('\n')) {
            return "\"" + text.replace("\"", "\"\"") + "\""
        }
        return text
    }

    private fun parseRow(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (quoted && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        quoted = !quoted
                    }
                }
                ch == ',' && !quoted -> {
                    values += current.toString()
                    current.setLength(0)
                }
                else -> current.append(ch)
            }
            i++
        }
        values += current.toString()
        return values
    }

    private fun parseDate(value: String): LocalDate? {
        return runCatching { LocalDate.parse(value) }.getOrNull()
            ?: runCatching { LocalDate.parse(value, legacyDateFormatter) }.getOrNull()
    }
}
