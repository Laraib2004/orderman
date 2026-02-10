package com.ordrino.orderman;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Environment;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.FirebaseFirestore;

import firebase.com.protolitewrapper.BuildConfig;

public class AppUpdater {
    private final Activity activity;
    private final FirebaseFirestore db;

    public AppUpdater(Activity activity) {
        this.activity = activity;
        this.db = FirebaseFirestore.getInstance();
    }

    public void checkForUpdate() {
        // 1. Get current version
        int currentVersionCode = BuildConfig.VERSION_CODE;

        // 2. Check Firestore for latest version
        db.collection("config").document("app_update").get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        double latestVersion = document.getDouble("version_code");
                        String downloadUrl = document.getString("apk_url");

                        if (latestVersion > currentVersionCode) {
                            showUpdateDialog(downloadUrl);
                        }
                    }
                });
    }

    private void showUpdateDialog(String url) {
        new AlertDialog.Builder(activity)
                .setTitle("New Update Available")
                .setMessage("A new version of Ordrino is ready. Please update now.")
                .setCancelable(false) // Force update
                .setPositiveButton("Update", (dialog, which) -> downloadAndInstall(url))
                .show();
    }

    private void downloadAndInstall(String url) {
        // Use Android DownloadManager to fetch the APK
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "ordrino_update.apk");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = manager.enqueue(request);

        // Listen for download completion
        BroadcastReceiver onComplete = new BroadcastReceiver() {
            public void onReceive(Context ctxt, Intent intent) {
                // Open the APK to install it
                Intent install = new Intent(Intent.ACTION_VIEW);
                Uri apkUri = manager.getUriForDownloadedFile(downloadId);

                install.setDataAndType(apkUri, "application/vnd.android.package-archive");
                install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                activity.startActivity(install);
                activity.unregisterReceiver(this);
            }
        };
        ContextCompat.registerReceiver(activity, onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_NOT_EXPORTED);
    }
}