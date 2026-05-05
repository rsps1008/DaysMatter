package com.rsps1008.daymatter.data

import com.rsps1008.daymatter.R
import java.time.LocalDate
import java.time.LocalTime

enum class EventCategory(val label: String, val iconRes: Int) {
    BIRTHDAY("生日", R.drawable.fa_cake_candles),
    ANNIVERSARY("紀念日", R.drawable.fa_champagne_glasses),
    WORK("工作", R.drawable.fa_briefcase),
    LIFE("生活", R.drawable.fa_tree_city),
    OTHER("其他", R.drawable.fa_paperclip);
}

enum class RepeatType(val label: String) {
    NONE("不循環"),
    YEARLY("每年"),
    MONTHLY("每月"),
    WEEKLY("每週"),
    CUSTOM("每 X 天");
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

enum class CategoryFilter(val label: String) {
    ALL("全部"),
    BIRTHDAY("生日"),
    ANNIVERSARY("紀念日"),
    WORK("工作"),
    LIFE("生活"),
    OTHER("其他");

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
