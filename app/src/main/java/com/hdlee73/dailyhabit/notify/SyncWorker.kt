package com.hdlee73.dailyhabit.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hdlee73.dailyhabit.data.PhoneSync
import java.util.concurrent.TimeUnit

/** PC와 짝을 맺었으면 와이파이에 있는 동안 30분마다, 그리고 앱을 열고 닫을 때 동기화한다 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!PhoneSync.status(applicationContext).value!!.paired) return Result.success()
        // 밖에 있어서 PC를 못 찾는 것은 오류가 아니다 (다음에 다시)
        PhoneSync.syncNow(applicationContext, quiet = true)
        return Result.success()
    }

    companion object {
        private val wifi get() = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES).setConstraints(wifi).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("pc-sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        /** 곧 한 번 (앱을 열거나 닫을 때) */
        fun soon(context: Context) {
            if (!PhoneSync.status(context).value!!.paired) return
            val req = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(wifi).build()
            WorkManager.getInstance(context).enqueueUniqueWork("pc-sync-now", ExistingWorkPolicy.KEEP, req)
        }
    }
}
