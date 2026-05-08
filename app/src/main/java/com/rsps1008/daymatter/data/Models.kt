package com.rsps1008.daymatter.data

import com.rsps1008.daymatter.R
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

enum class EventCategory(private val zhLabel: String, private val enLabel: String, val iconRes: Int) {
    BIRTHDAY("生日", "Birthday", R.drawable.fa_cake_candles),
    ANNIVERSARY("紀念日", "Anniversary", R.drawable.fa_champagne_glasses),
    WORK("工作", "Work", R.drawable.fa_briefcase),
    LIFE("生活", "Life", R.drawable.fa_tree_city),
    OTHER("其他", "Other", R.drawable.fa_paperclip);

    val label: String
        get() = if (Locale.getDefault().language.startsWith("en")) enLabel else zhLabel
}

enum class RepeatType(private val zhLabel: String, private val enLabel: String) {
    NONE("不循環", "No repeat"),
    YEARLY("每年", "Yearly"),
    MONTHLY("每月", "Monthly"),
    WEEKLY("每週", "Weekly"),
    CUSTOM("每 X 天", "Every X days");

    val label: String
        get() = if (Locale.getDefault().language.startsWith("en")) enLabel else zhLabel
}

data class EventItem(
    val id: Long = 0L,
    val title: String,
    val category: EventCategory,
    val date: LocalDate,
    val repeatType: RepeatType = RepeatType.NONE,
    val repeatInterval: Int = 0,
    val enableReminder: Boolean = false,
    val reminderTime: LocalTime? = null,
    val showInWidget: Boolean = false,
    val createTime: Long = System.currentTimeMillis(),
)

enum class CategoryFilter(private val zhLabel: String, private val enLabel: String) {
    ALL("全部", "All"),
    BIRTHDAY("生日", "Birthday"),
    ANNIVERSARY("紀念日", "Anniversary"),
    WORK("工作", "Work"),
    LIFE("生活", "Life"),
    OTHER("其他", "Other");

    val label: String
        get() = if (Locale.getDefault().language.startsWith("en")) enLabel else zhLabel

    fun matches(category: EventCategory): Boolean {
        return when (this) {
            ALL -> true
            BIRTHDAY -> category == EventCategory.BIRTHDAY
            ANNIVERSARY -> category == EventCategory.ANNIVERSARY
            WORK -> category == EventCategory.WORK
            LIFE -> category == EventCategory.LIFE
            OTHER -> category == EventCategory.OTHER
        }
    }
}
