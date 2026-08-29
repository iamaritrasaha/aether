package com.foresightlabs.aether.data.push

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.foresightlabs.aether.AetherApplication

/**
 * The one continuation path a push can hand off to: TDLib error 406 means
 * the push alone did not carry enough to update state from, and a live
 * connection is required to fetch what changed. Everything else --
 * successful processing, GetPushReceiverId routing, any other error --
 * finishes inside the FCM callback itself and never reaches this class. It
 * does not need (and is never given) the push payload itself: TDLib already
 * saw it via ProcessPushNotification, and this worker's only job is
 * "connect and fetch whatever is pending" -- there is nothing push-specific
 * left to act on, so nothing private is carried into WorkManager's stored
 * input data.
 *
 * Uses an ordinary [OneTimeWorkRequest][androidx.work.OneTimeWorkRequest]:
 * no `setExpedited()`, no `setForeground()`, no `ForegroundInfo` is ever
 * provided by this class. This push continuation is intentionally
 * non-expedited to avoid introducing a foreground-service requirement on
 * older Android versions.
 */
class PushFetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val telegram = (applicationContext as? AetherApplication)?.telegram
            // The application itself isn't the type this process should have
            // -- a broken condition, not something retrying will fix.
            ?: return Result.failure()
        return when (telegram.awaitPushFetchCompletion()) {
            PushPendingGate.Outcome.COMPLETED -> Result.success()
            // A timeout is never proof of anything -- it means TDLib had not
            // reported completion within the bound, not that it succeeded.
            // WorkManager's own exponential backoff governs the wait between
            // attempts; runAttemptCount bounds the attempts themselves so
            // this does not retry forever against, e.g., a genuinely
            // unreachable network.
            PushPendingGate.Outcome.TIMED_OUT ->
                if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 3
    }
}
