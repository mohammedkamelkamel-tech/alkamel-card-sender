package ye.alkamel.cardsender

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class BackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result =
        if (BackupManager.createBackup(applicationContext) != null) Result.success() else Result.retry()
}
