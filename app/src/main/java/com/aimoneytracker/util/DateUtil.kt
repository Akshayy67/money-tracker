package com.aimoneytracker.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Time helpers built on java.time (minSdk 26). All ranges are inclusive of start, exclusive of end. */
object DateUtil {

    val zone: ZoneId = ZoneId.systemDefault()

    fun now(): Long = System.currentTimeMillis()

    fun toLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun toLocalDateTime(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDay(date: LocalDate): Long =
        date.atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant().toEpochMilli()

    fun startOfMonth(ref: Long = now()): Long {
        val d = toLocalDate(ref).withDayOfMonth(1)
        return startOfDay(d)
    }

    fun endOfMonth(ref: Long = now()): Long {
        val d = toLocalDate(ref).with(TemporalAdjusters.lastDayOfMonth())
        return endOfDay(d)
    }

    fun startOfWeek(ref: Long = now()): Long {
        val d = toLocalDate(ref).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return startOfDay(d)
    }

    fun endOfWeek(ref: Long = now()): Long {
        val d = toLocalDate(ref).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return endOfDay(d)
    }

    fun startOfYear(ref: Long = now()): Long {
        val d = toLocalDate(ref).withDayOfYear(1)
        return startOfDay(d)
    }

    fun monthsAgo(months: Int, ref: Long = now()): Long {
        val d = toLocalDate(ref).minusMonths(months.toLong())
        return startOfDay(d)
    }

    fun daysAgo(days: Int, ref: Long = now()): Long =
        startOfDay(toLocalDate(ref).minusDays(days.toLong()))

    fun daysInMonth(ref: Long = now()): Int = YearMonth.from(toLocalDate(ref)).lengthOfMonth()

    fun dayOfMonth(ref: Long = now()): Int = toLocalDate(ref).dayOfMonth

    fun remainingDaysInMonth(ref: Long = now()): Int = daysInMonth(ref) - dayOfMonth(ref)

    fun monthKey(ref: Long = now()): String =
        toLocalDate(ref).format(DateTimeFormatter.ofPattern("yyyy-MM"))

    fun dayName(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    fun isWeekend(millis: Long): Boolean {
        val dow = toLocalDate(millis).dayOfWeek
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
    }

    fun format(millis: Long, pattern: String = "dd MMM yyyy"): String =
        toLocalDateTime(millis).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))

    /** Next occurrence of a given day-of-month, on/after [from]. */
    fun nextDayOfMonth(dayOfMonth: Int, from: Long = now()): Long {
        var date = toLocalDate(from)
        val capped = dayOfMonth.coerceIn(1, 28)
        date = if (date.dayOfMonth <= capped) date.withDayOfMonth(capped)
        else date.plusMonths(1).withDayOfMonth(capped)
        return startOfDay(date)
    }
}
