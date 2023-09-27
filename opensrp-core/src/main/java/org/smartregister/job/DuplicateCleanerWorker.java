package org.smartregister.job;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.smartregister.domain.DuplicateZeirIdStatus;
import org.smartregister.util.AppHealthUtils;

import java.util.concurrent.TimeUnit;

import timber.log.Timber;

public class DuplicateCleanerWorker extends Worker {
    private Context mContext;
    public static final String TAG = "DuplicateCleanerWorker";

    public DuplicateCleanerWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mContext = context;
    }

    /**
     * Schedule this job to run periodically
     *
     * @param context
     * @param mins - Duration after which the job repeatedly runs. This should be at least 15 mins
     */
    public static void scheduleJob(Context context, int mins, String[] eventTypes) {
        Timber.i("Scheduling DuplicateCleanerWorker job");
        Data data = new Data.Builder().putStringArray("eventTypes", eventTypes).build();

        PeriodicWorkRequest periodicWorkRequest = new PeriodicWorkRequest.Builder(DuplicateCleanerWorker.class, mins, TimeUnit.MINUTES)
                .setInputData(data)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.KEEP, periodicWorkRequest);

    }

    @NonNull
    @Override
    public Result doWork() {
        String[] eventTypes = getInputData().getStringArray("eventTypes");
        DuplicateZeirIdStatus duplicateZeirIdStatus = AppHealthUtils.cleanUniqueZeirIds(eventTypes);
        Timber.i("Doing some cleaning work");
        if (duplicateZeirIdStatus != null && duplicateZeirIdStatus.equals(DuplicateZeirIdStatus.CLEANED))
            WorkManager.getInstance(mContext).cancelWorkById(this.getId());

        return Result.success();
    }
}
