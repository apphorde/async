package com.example.storagereader;
import android.content.Context;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;
class SyncScheduler { static void schedule(Context context) { Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build(); PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES).setConstraints(constraints).build(); WorkManager.getInstance(context).enqueueUniquePeriodicWork("reader-vault-sync", ExistingPeriodicWorkPolicy.UPDATE, request); } }
