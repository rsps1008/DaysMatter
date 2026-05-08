package com.rsps1008.daymatter.ui

import java.util.Locale
import kotlin.math.abs

object UiText {
    private fun isEnglish(): Boolean {
        return Locale.getDefault().language.startsWith("en")
    }

    private fun text(zh: String, en: String): String {
        return if (isEnglish()) en else zh
    }

    fun appTitle(): String = "DayMatter"
    fun eventsCount(count: Int): String = text("$count 個事件", "$count events")
    fun settings(): String = text("設定", "Settings")
    fun addEvent(): String = text("新增事件", "Add event")
    fun notificationPermission(): String = text("通知權限", "Notification permission")
    fun notificationPermissionLine1(): String = text("通知權限已關閉，系統可能不再跳出授權視窗。", "Notifications are off, so the system may not show the permission prompt again.")
    fun notificationPermissionLine2(): String = text("請到通知設定頁手動開啟，才能收到提醒。", "Please open notification settings manually to receive reminders.")
    fun openSettings(): String = text("開啟設定", "Open settings")
    fun cancel(): String = text("取消", "Cancel")

    fun exportDone(): String = text("已完成匯出", "Export completed")
    fun googleDriveCancelled(): String = text("Google Drive 已取消", "Google Drive cancelled")
    fun googleDriveAuthFailed(): String = text("Google Drive 授權失敗", "Google Drive authorization failed")
    fun googleDriveBackupDone(): String = text("已備份到 Google Drive", "Backed up to Google Drive")
    fun googleDriveRestoreDone(): String = text("已從 Google Drive 還原", "Restored from Google Drive")
    fun googleDriveOperationFailed(message: String): String = text("Google Drive 操作失敗：$message", "Google Drive operation failed: $message")
    fun googleDriveBackupFailed(message: String): String = text("Google Drive 備份失敗：$message", "Google Drive backup failed: $message")
    fun googleDriveRestoreFailed(message: String): String = text("Google Drive 還原失敗：$message", "Google Drive restore failed: $message")
    fun googleDriveUnavailable(): String = text("目前無法使用 Google Drive", "Google Drive is currently unavailable")

    fun importCsvTitle(): String = text("匯入 CSV", "Import CSV")
    fun importCsvQuestion(): String = text("要怎麼處理目前已有的資料？", "How should we handle the existing data?")
    fun importKeepExisting(): String = text("保留現有資料匯入", "Import and keep existing data")
    fun importReplaceExisting(): String = text("先刪除現有資料再匯入", "Delete existing data first")
    fun importResult(imported: Int, skipped: Int, replacedExisting: Boolean): String {
        return if (replacedExisting) {
            text("已清空現有資料後匯入 $imported 筆，略過 $skipped 筆", "Imported $imported items after clearing existing data, skipped $skipped")
        } else {
            text("匯入 $imported 筆，略過 $skipped 筆", "Imported $imported items, skipped $skipped")
        }
    }

    fun deleteAllTitle(): String = text("刪除全部資料", "Delete all data")
    fun deleteAllBody(): String = text("這會刪除目前所有事件，無法復原。要繼續嗎？", "This will delete all current events and cannot be undone. Continue?")
    fun deleteAllDone(): String = text("已刪除全部資料", "All data deleted")
    fun delete(): String = text("刪除", "Delete")
    fun restoreTitle(): String = text("Google Drive 還原", "Google Drive restore")
    fun restoreBody(): String = text("你可以選擇保留現有資料直接合併，或先清空目前資料再還原 Google Drive 私有備份。", "You can keep existing data and merge, or clear current data first and restore the private Google Drive backup.")
    fun restoreKeepExisting(): String = text("保留現有資料還原", "Restore and keep existing data")
    fun restoreReplaceExisting(): String = text("先清空再還原", "Clear then restore")

    fun noEventsTitle(): String = text("還沒有事件", "No events yet")
    fun noEventsBody(): String = text("按右下角新增第一筆倒數日。", "Tap the bottom-right button to add your first countdown.")

    fun save(): String = text("儲存", "Save")
    fun editEventTitle(isNew: Boolean): String = if (isNew) text("新增事件", "Add event") else text("編輯事件", "Edit event")
    fun eventName(): String = text("事件名稱", "Event name")
    fun category(): String = text("分類", "Category")
    fun date(): String = text("日期", "Date")
    fun repeatRule(): String = text("循環規則", "Repeat rule")
    fun repeatEveryXDays(): String = text("每幾天循環", "Repeat every X days")
    fun enableReminder(): String = text("啟用提醒", "Enable reminder")
    fun showInWidget(): String = text("顯示於桌面 Widget", "Show on home screen widget")
    fun localData(): String = text("本地資料", "Local data")
    fun localDataDescription(): String = text("匯入或匯出 CSV，也可以直接清除目前資料。", "Import or export CSV, or clear the current data.")
    fun importCsv(): String = text("匯入 CSV", "Import CSV")
    fun exportCsv(): String = text("匯出 CSV", "Export CSV")
    fun deleteAllData(): String = text("刪除全部資料", "Delete all data")
    fun googleDrive(): String = "Google Drive"
    fun googleDriveDescription(): String = text("使用私有 appData 備份與還原，不會出現在可見雲端資料夾。", "Use private appData for backup and restore. It won't appear in visible Drive folders.")
    fun googleDriveBackup(): String = text("Google Drive 備份", "Google Drive backup")
    fun googleDriveRestore(): String = text("Google Drive 還原", "Google Drive restore")
    fun close(): String = text("關閉", "Close")

    fun reminderChannelName(): String = text("倒數日提醒", "DayMatter reminders")
    fun reminderChannelDescription(): String = text("事件提醒通知", "Event reminder notifications")
    fun reminderToday(): String = text("今天到了", "Today is here")
    fun reminderInDays(days: Long): String = text("還有 $days 天", "In $days days")

    fun widgetEmptyTitle(): String = text("尚未設定 Widget 事件", "No widget event set")
    fun widgetEmptyCategory(): String = text("請設定", "Set up")
    fun widgetEmptySubtitle(): String = text("請在事件中勾選「顯示於桌面」", "Enable \"Show on home screen\" for an event")
    fun widgetUnit(count: Long): String = if (isEnglish()) if (count == 1L) "day" else "days" else "天"

    fun countdownToday(): String = text("今天", "Today")
    fun countdownDays(days: Long): String = text("${abs(days)} 天", "${abs(days)} days")
    fun countdownPast(days: Long): String = text("已過 ${abs(days)} 天", "${abs(days)} days ago")
    fun countdownEveryYear(date: String): String = text("每年 $date", "Every year $date")
    fun countdownEveryMonth(date: String): String = text("每月 $date", "Every month $date")
    fun countdownEveryWeek(day: String): String = text("每週 $day", "Every week $day")
    fun countdownEveryXDays(interval: Int): String = text("每 $interval 天", "Every $interval days")
    fun countdownNotification(days: Long): String = if (days <= 0) reminderToday() else reminderInDays(days)
}
