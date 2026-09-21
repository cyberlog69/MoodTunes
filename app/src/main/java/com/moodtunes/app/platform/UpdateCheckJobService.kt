package com.moodtunes.app.platform

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.moodtunes.app.data.remote.UpdateChecker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class UpdateCheckJobService : JobService() {

    @Inject
    lateinit var updateChecker: UpdateChecker

    @Inject
    lateinit var appUpdateNotifier: AppUpdateNotifier

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartJob(params: JobParameters?): Boolean {
        Timber.d("UpdateCheckJobService: Starting background update check")
        serviceScope.launch {
            try {
                val result = updateChecker.checkForUpdates(force = false)
                if (result.isUpdateAvailable) {
                    Timber.i("UpdateCheckJobService: New version found: %s", result.latestVersion)
                    appUpdateNotifier.notifyUpdate(result, force = false)
                } else {
                    Timber.d("UpdateCheckJobService: App is up to date (%s)", result.currentVersion)
                }
                jobFinished(params, false)
            } catch (e: Exception) {
                Timber.e(e, "UpdateCheckJobService: Background update check encountered an error")
                jobFinished(params, false)
            }
        }
        return true // Returning true indicates ongoing asynchronous work
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Timber.d("UpdateCheckJobService: Job stopped prematurely")
        serviceScope.cancel()
        return true // Reschedule if job was interrupted before finishing
    }

    companion object {
        const val JOB_ID = 9101
        private val INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(24)

        fun schedule(context: Context) {
            try {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return

                // Avoid re-scheduling if already active
                val isAlreadyScheduled = scheduler.allPendingJobs.any { it.id == JOB_ID }
                if (isAlreadyScheduled) {
                    Timber.d("UpdateCheckJobService: Already scheduled, skipping duplicate schedule")
                    return
                }

                val component = ComponentName(context, UpdateCheckJobService::class.java)
                val jobInfo = JobInfo.Builder(JOB_ID, component)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(INTERVAL_MILLIS)
                    .setPersisted(true)
                    .build()

                val resultCode = scheduler.schedule(jobInfo)
                if (resultCode == JobScheduler.RESULT_SUCCESS) {
                    Timber.i("UpdateCheckJobService: Successfully scheduled periodic 24h background check")
                } else {
                    Timber.w("UpdateCheckJobService: Failed to schedule (code: %d)", resultCode)
                }
            } catch (e: Exception) {
                Timber.e(e, "UpdateCheckJobService: Exception while scheduling job")
            }
        }

        fun cancel(context: Context) {
            try {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
                scheduler.cancel(JOB_ID)
                Timber.i("UpdateCheckJobService: Cancelled periodic update check job")
            } catch (e: Exception) {
                Timber.w(e, "UpdateCheckJobService: Failed to cancel job")
            }
        }
    }
}
