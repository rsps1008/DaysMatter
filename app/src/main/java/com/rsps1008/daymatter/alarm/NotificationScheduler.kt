package com.rsps1008.daymatter.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rsps1008.daymatter.R
import com.rsps1008.daymatter.data.CountdownLogic
import com.rsps1008.daymatter.data.DayMatterRepository
import com.rsps1008.daymatter.data.EventItem
import com.rsps1008.daymatter.data.RepeatType
import com.rsps1008.daymatter.ui.UiText
import java.time.LocalDate
import java.time.ZoneId

object NotificationScheduler {
    const val CHANNEL_ID = "daymatter_reminders"
    const val EXTRA_EVENT_ID = "extra_event_id"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            UiText.reminderChannelName(),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = UiText.reminderChannelDescription()
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun syncAll(context: Context, events: List<EventItem>) {
        val enabledIds = events.filter { it.enableReminder }.map { it.id }.toSet()
        events.forEach { schedule(context, it) }
        DayMatterRepository.currentEvents()
            .filter { it.id !in enabledIds }
            .forEach { cancel(context, it.id) }
    }

    fun schedule(context: Context, event: EventItem) {
        cancel(context, event.id)
        if (!event.enableReminder || event.reminderTime == null) return

        val reminderTime = CountdownLogic.reminderDateTime(event) ?: return
        var triggerAt = reminderTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        if (triggerAt <= now && event.repeatType == RepeatType.NONE) return
        if (triggerAt <= now && event.repeatType != RepeatType.NONE) {
            val nextDate = CountdownLogic.resolveTargetDate(event, LocalDate.now().plusDays(1))
            triggerAt = nextDate.atTime(event.reminderTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

        val pendingIntent = pendingIntent(context, event.id) ?: return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    fun cancel(context: Context, eventId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val existing = pendingIntent(context, eventId, PendingIntent.FLAG_NO_CREATE)
        if (existing != null) {
            alarmManager.cancel(existing)
        }
    }

    fun postReminder(context: Context, event: EventItem) {
        createChannel(context)
        val countdown = CountdownLogic.resolveCountdown(event)
        val text = UiText.countdownNotification(countdown.days)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(event.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text · ${countdown.subtitle}"))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(event.id.toInt(), notification)
    }

    private fun pendingIntent(context: Context, eventId: Long, flagOverride: Int? = null): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_EVENT_ID, eventId)
        val flags = (flagOverride ?: (PendingIntent.FLAG_UPDATE_CURRENT)) or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, eventId.toInt(), intent, flags)
    }
}
