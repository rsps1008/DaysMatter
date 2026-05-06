package com.rsps1008.daymatter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.RemoteViews
import com.rsps1008.daymatter.MainActivity
import com.rsps1008.daymatter.R
import com.rsps1008.daymatter.data.CountdownLogic
import com.rsps1008.daymatter.data.DayMatterRepository
import com.rsps1008.daymatter.data.EventItem
import java.time.format.DateTimeFormatter
import java.util.Locale

class DayMatterWidgetProvider : AppWidgetProvider() {
    private val widgetDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.TAIWAN)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        when (action) {
            ACTION_WIDGET_NEXT_EVENT -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    advanceWidgetSelection(context, widgetId)
                }
                requestUpdate(context)
            }
            ACTION_WIDGET_RESET_NEAREST -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    resetWidgetSelection(context, widgetId)
                }
                requestUpdate(context)
            }
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                requestUpdate(context)
            }
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_daymatter)
        val event = resolveCurrentEvent(context, widgetId)
        if (event == null) {
            remoteViews.setTextViewText(R.id.widgetTitle, "尚未設定 Widget 事件")
            remoteViews.setTextViewText(R.id.widgetCategory, "請設定")
            remoteViews.setTextViewText(R.id.widgetSubtitle, "請在事件中勾選「顯示於桌面」")
            remoteViews.setTextViewText(R.id.widgetIconDay, "")
            remoteViews.setTextViewText(R.id.widgetCountdownNumber, "0")
            remoteViews.setTextViewText(R.id.widgetCountdownUnit, "天")
        } else {
            val countdown = CountdownLogic.resolveCountdown(event)
            val countNumber = when {
                countdown.days == 0L -> "0"
                else -> kotlin.math.abs(countdown.days).toString()
            }
            remoteViews.setTextViewText(R.id.widgetTitle, event.title)
            remoteViews.setTextViewText(R.id.widgetCategory, event.category.label)
            remoteViews.setTextViewText(R.id.widgetSubtitle, countdown.targetDate.format(widgetDateFormatter))
            remoteViews.setTextViewText(R.id.widgetIconDay, event.date.dayOfMonth.toString())
            remoteViews.setTextViewText(R.id.widgetCountdownNumber, countNumber)
            remoteViews.setTextViewText(R.id.widgetCountdownUnit, "天")
        }
        val launchIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val nextIntent = widgetBroadcastIntent(context, widgetId, ACTION_WIDGET_NEXT_EVENT)
        val resetIntent = widgetBroadcastIntent(context, widgetId, ACTION_WIDGET_RESET_NEAREST)
        remoteViews.setOnClickPendingIntent(
            R.id.widgetIcon,
            launchIntent
        )
        remoteViews.setOnClickPendingIntent(
            R.id.widgetIconContainer,
            launchIntent
        )
        remoteViews.setOnClickPendingIntent(
            R.id.widgetIconDay,
            launchIntent
        )
        remoteViews.setOnClickPendingIntent(
            R.id.widgetRoot,
            nextIntent
        )
        remoteViews.setOnClickPendingIntent(
            R.id.widgetTitle,
            nextIntent
        )
        remoteViews.setOnClickPendingIntent(
            R.id.widgetCategory,
            nextIntent
        )
        remoteViews.setOnClickPendingIntent(
            R.id.widgetSubtitle,
            nextIntent
        )
        remoteViews.setOnClickPendingIntent(
            R.id.widgetCountdownNumber,
            resetIntent
        )
        remoteViews.setOnClickPendingIntent(
            R.id.widgetCountdownUnit,
            resetIntent
        )
        manager.updateAppWidget(widgetId, remoteViews)
    }

    private fun resolveCurrentEvent(context: Context, widgetId: Int): EventItem? {
        val events = widgetEvents()
        if (events.isEmpty()) {
            clearSelection(context, widgetId)
            return null
        }

        val selectedId = preferences(context).getLong(selectionKey(widgetId), 0L)
        val selected = events.firstOrNull { it.id == selectedId }
        if (selected != null) {
            return selected
        }

        val nearest = events.first()
        saveSelection(context, widgetId, nearest.id)
        return nearest
    }

    private fun advanceWidgetSelection(context: Context, widgetId: Int): EventItem? {
        val events = widgetEvents()
        if (events.isEmpty()) {
            clearSelection(context, widgetId)
            return null
        }

        val currentId = preferences(context).getLong(selectionKey(widgetId), 0L)
        val currentIndex = events.indexOfFirst { it.id == currentId }
        val nextIndex = if (currentIndex >= 0) {
            (currentIndex + 1) % events.size
        } else {
            0
        }
        val next = events[nextIndex]
        saveSelection(context, widgetId, next.id)
        return next
    }

    private fun resetWidgetSelection(context: Context, widgetId: Int): EventItem? {
        val events = widgetEvents()
        if (events.isEmpty()) {
            clearSelection(context, widgetId)
            return null
        }

        val nearest = events.first()
        saveSelection(context, widgetId, nearest.id)
        return nearest
    }

    private fun widgetEvents(): List<EventItem> {
        return DayMatterRepository.currentEvents()
            .filter { it.showInWidget }
            .filter { CountdownLogic.resolveCountdown(it).days >= 0L }
            .sortedWith(compareBy<EventItem> { CountdownLogic.resolveCountdown(it).days }.thenBy { it.title })
    }

    private fun widgetBroadcastIntent(context: Context, widgetId: Int, action: String): PendingIntent {
        val intent = Intent(context, DayMatterWidgetProvider::class.java).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        val requestCode = widgetRequestCode(widgetId, action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun preferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
    }

    private fun saveSelection(context: Context, widgetId: Int, eventId: Long) {
        preferences(context).edit().putLong(selectionKey(widgetId), eventId).apply()
    }

    private fun saveSelectionForAllWidgets(context: Context, eventId: Long) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, DayMatterWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        ids.forEach { widgetId ->
            saveSelection(context, widgetId, eventId)
        }
    }

    private fun clearSelection(context: Context, widgetId: Int) {
        preferences(context).edit().remove(selectionKey(widgetId)).apply()
    }

    private fun selectionKey(widgetId: Int): String {
        return "${SELECTION_PREFIX}_$widgetId"
    }

    private fun widgetRequestCode(widgetId: Int, action: String): Int {
        return 31 * widgetId + action.hashCode()
    }

    companion object {
        private const val WIDGET_PREFS = "daymatter_widget_state"
        private const val SELECTION_PREFIX = "selected_event_id"
        private const val ACTION_WIDGET_NEXT_EVENT = "com.rsps1008.daymatter.widget.ACTION_WIDGET_NEXT_EVENT"
        private const val ACTION_WIDGET_RESET_NEAREST = "com.rsps1008.daymatter.widget.ACTION_WIDGET_RESET_NEAREST"

        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, DayMatterWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                DayMatterWidgetProvider().onUpdate(context, manager, ids)
            }
        }

        fun pinEventToWidget(context: Context, eventId: Long) {
            DayMatterWidgetProvider().saveSelectionForAllWidgets(context, eventId)
            requestUpdate(context)
        }

        fun isNearestWidgetEvent(context: Context, eventId: Long): Boolean {
            return DayMatterWidgetProvider().widgetEvents().firstOrNull()?.id == eventId
        }
    }
}
