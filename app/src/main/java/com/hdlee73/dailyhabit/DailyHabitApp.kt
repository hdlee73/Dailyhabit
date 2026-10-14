package com.hdlee73.dailyhabit

import android.app.Application
import com.hdlee73.dailyhabit.notify.DailyScheduler
import com.hdlee73.dailyhabit.notify.Notifier
import com.hdlee73.dailyhabit.notify.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DailyHabitApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        DailyScheduler.schedule(this)
        com.hdlee73.dailyhabit.notify.UpdateWorker.schedule(this)
        com.hdlee73.dailyhabit.notify.SyncWorker.schedule(this)
        appScope.launch {
            com.hdlee73.dailyhabit.data.FaithRoutines.seed(this@DailyHabitApp)
            Reminders.rescheduleAll(this@DailyHabitApp)
        }
    }
}
