package org.smartregister.sync.intent;

import android.app.IntentService;
import android.content.Intent;

import org.smartregister.sync.helper.TaskServiceHelper;

public class SyncTaskIntentService extends IntentService {
    private static final String TAG = "SyncTaskIntentService";
    private TaskServiceHelper taskServiceHelper;

    public SyncTaskIntentService() {
        super(TAG);
    }

    public SyncTaskIntentService(TaskServiceHelper taskServiceHelper) {
        super(TAG);
        this.taskServiceHelper = taskServiceHelper;
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        if (taskServiceHelper == null) {
            taskServiceHelper = TaskServiceHelper.getInstance();
        }
        taskServiceHelper.syncTasks();

    }

}