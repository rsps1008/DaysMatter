package com.rsps1008.daymatter

import android.app.Application
import com.rsps1008.daymatter.alarm.NotificationScheduler
import com.rsps1008.daymatter.data.DayMatterRepository

class DayMatterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DayMatterRepository.initialize(this)
        NotificationScheduler.createChannel(this)
    }
}
