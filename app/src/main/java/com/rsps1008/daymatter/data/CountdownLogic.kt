package com.rsps1008.daymatter.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.format.DateTimeFormatter
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

data class CountdownInfo(
    val targetDate: LocalDate,
    val days: Long,
    val displayText: String,
    val subtitle: String,
)

object CountdownLogic {
    private val yearlyFormatter = DateTimeFormatter.ofPattern("MM-dd")
    private val monthlyFormatter = DateTimeFormatter.ofPattern("dd")

    fun resolveTargetDate(event: EventItem, today: LocalDate = LocalDate.now()): LocalDate {
        return when (event.repeatType) {
            RepeatType.NONE -> event.date
            RepeatType.YEARLY -> resolveYearly(event.date, today)
            RepeatType.MONTHLY -> resolveMonthly(event.date, today)
            RepeatType.WEEKLY -> resolveWeekly(event.date, today)
            RepeatType.CUSTOM -> resolveCustom(event.date, event.repeatInterval, today)
        }
    }

    fun resolveCountdown(event: EventItem, today: LocalDate = LocalDate.now()): CountdownInfo {
        val target = resolveTargetDate(event, today)
        val days = ChronoUnit.DAYS.between(today, target)
        val displayText = when {
            days == 0L -> localizedToday()
            days > 0 -> localizedDays(days)
            else -> localizedPast(days)
        }
        val subtitle = when (event.repeatType) {
            RepeatType.NONE -> target.toString()
            RepeatType.YEARLY -> localizedEveryYear(event.date.format(yearlyFormatter))
            RepeatType.MONTHLY -> localizedEveryMonth(event.date.format(monthlyFormatter))
            RepeatType.WEEKLY -> localizedEveryWeek(target.dayOfWeek.toLocalizedShort())
            RepeatType.CUSTOM -> localizedEveryXDays(event.repeatInterval)
        }
        return CountdownInfo(target, days, displayText, subtitle)
    }

    fun reminderDateTime(event: EventItem, today: LocalDate = LocalDate.now()): LocalDateTime? {
        val time = event.reminderTime ?: return null
        val targetDate = resolveTargetDate(event, today)
        return targetDate.atTime(time)
    }

    private fun resolveYearly(date: LocalDate, today: LocalDate): LocalDate {
        val candidate = safeDate(today.year, date.monthValue, date.dayOfMonth)
        return if (candidate.isBefore(today)) safeDate(today.year + 1, date.monthValue, date.dayOfMonth) else candidate
    }

    private fun resolveMonthly(date: LocalDate, today: LocalDate): LocalDate {
        val candidate = safeDate(today.year, today.monthValue, date.dayOfMonth)
        return if (candidate.isBefore(today)) {
            val next = today.plusMonths(1)
            safeDate(next.year, next.monthValue, date.dayOfMonth)
        } else candidate
    }

    private fun resolveWeekly(date: LocalDate, today: LocalDate): LocalDate {
        val candidate = today.with(TemporalAdjusters.nextOrSame(date.dayOfWeek))
        return if (candidate.isBefore(date)) date else candidate
    }

    private fun resolveCustom(date: LocalDate, interval: Int, today: LocalDate): LocalDate {
        if (interval <= 0) return date
        if (today.isBefore(date) || today.isEqual(date)) return date
        val daysPassed = ChronoUnit.DAYS.between(date, today)
        val remain = daysPassed % interval
        return if (remain == 0L) today else today.plusDays(interval - remain)
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate {
        val maxDay = YearMonth.of(year, month).lengthOfMonth()
        return LocalDate.of(year, month, minOf(day, maxDay))
    }
}

private fun localizedToday(): String {
    return if (Locale.getDefault().language.startsWith("en")) "Today" else "今天"
}

private fun localizedDays(days: Long): String {
    return if (Locale.getDefault().language.startsWith("en")) "$days days" else "$days 天"
}

private fun localizedPast(days: Long): String {
    return if (Locale.getDefault().language.startsWith("en")) "${kotlin.math.abs(days)} days ago" else "已過 ${kotlin.math.abs(days)} 天"
}

private fun localizedEveryYear(date: String): String {
    return if (Locale.getDefault().language.startsWith("en")) "Every year $date" else "每年 $date"
}

private fun localizedEveryMonth(date: String): String {
    return if (Locale.getDefault().language.startsWith("en")) "Every month $date" else "每月 $date"
}

private fun localizedEveryWeek(day: String): String {
    return if (Locale.getDefault().language.startsWith("en")) "Every week $day" else "每週 $day"
}

private fun localizedEveryXDays(interval: Int): String {
    return if (Locale.getDefault().language.startsWith("en")) "Every $interval days" else "每 $interval 天"
}

private fun DayOfWeek.toLocalizedShort(): String {
    return if (Locale.getDefault().language.startsWith("en")) {
        getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    } else {
        when (this) {
            DayOfWeek.MONDAY -> "一"
            DayOfWeek.TUESDAY -> "二"
            DayOfWeek.WEDNESDAY -> "三"
            DayOfWeek.THURSDAY -> "四"
            DayOfWeek.FRIDAY -> "五"
            DayOfWeek.SATURDAY -> "六"
            DayOfWeek.SUNDAY -> "日"
        }
    }
}

fun LocalDateTime.toEpochMillis(zoneId: ZoneId = ZoneId.systemDefault()): Long {
    return atZone(zoneId).toInstant().toEpochMilli()
}
