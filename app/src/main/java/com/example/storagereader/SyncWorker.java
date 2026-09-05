package com.example.storagereader;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
public class SyncWorker extends Worker {
    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }
    @NonNull @Override public Result doWork() { try { SyncEngine.run(getApplicationContext(), ignored -> {}); return Result.success(); } catch (Exception e) { return Result.retry(); } }
}
