package com.rsps1008.daymatter.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rsps1008.daymatter.data.DayMatterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(NotificationScheduler.EXTRA_EVENT_ID, -1L)
        val event = DayMatterRepository.findById(eventId) ?: return
        CoroutineScope(Dispatchers.Default).launch {
            NotificationScheduler.postReminder(context.applicationContext, event)
        }
    }
}
