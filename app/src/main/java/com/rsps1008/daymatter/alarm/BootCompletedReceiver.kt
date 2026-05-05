package com.rsps1008.daymatter.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rsps1008.daymatter.data.DayMatterRepository
import com.rsps1008.daymatter.widget.DayMatterWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        CoroutineScope(Dispatchers.IO).launch {
            NotificationScheduler.syncAll(context.applicationContext, DayMatterRepository.currentEvents())
            DayMatterWidgetProvider.requestUpdate(context.applicationContext)
        }
    }
}
