package app.crewdatasaver.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AllowanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AllowanceChecker.check(applicationContext)
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "allowance_check"
    }
}
