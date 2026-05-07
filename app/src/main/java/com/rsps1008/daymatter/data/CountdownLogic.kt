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
            days == 0L -> "今天"
            days > 0 -> "$days 天"
            else -> "已過 ${kotlin.math.abs(days)} 天"
        }
        val subtitle = when (event.repeatType) {
            RepeatType.NONE -> target.toString()
            RepeatType.YEARLY -> "每年 ${event.date.format(yearlyFormatter)}"
            RepeatType.MONTHLY -> "每月 ${event.date.format(monthlyFormatter)}"
            RepeatType.WEEKLY -> "每週 ${target.dayOfWeek.toChineseShort()}"
            RepeatType.CUSTOM -> "每 ${event.repeatInterval} 天"
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

private fun DayOfWeek.toChineseShort(): String {
    return when (this) {
        DayOfWeek.MONDAY -> "一"
        DayOfWeek.TUESDAY -> "二"
        DayOfWeek.WEDNESDAY -> "三"
        DayOfWeek.THURSDAY -> "四"
        DayOfWeek.FRIDAY -> "五"
        DayOfWeek.SATURDAY -> "六"
        DayOfWeek.SUNDAY -> "日"
    }
}

fun LocalDateTime.toEpochMillis(zoneId: ZoneId = ZoneId.systemDefault()): Long {
    return atZone(zoneId).toInstant().toEpochMilli()
}
