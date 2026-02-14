package com.ordrino.orderman;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.firestore.FirebaseFirestore;

public class AppUpdater {

    private final Activity activity;
    private final FirebaseFirestore db;

    public AppUpdater(Activity activity) {
        this.activity = activity;
        this.db = FirebaseFirestore.getInstance();
    }

    public void checkForUpdate() {

        int currentVersionCode = BuildConfig.VERSION_CODE;

        db.collection("config")
                .document("app_update")
                .get()
                .addOnSuccessListener(document -> {

                    if (activity == null ||
                            activity.isFinishing() ||
                            activity.isDestroyed()) {
                        Log.d("AppUpdater", "Activity dead, skipping update check.");
                        return;
                    }

                    if (!document.exists()) return;

                    Double latestVersionDouble =
                            document.getDouble("version_code");

                    if (latestVersionDouble == null) return;

                    int latestVersion = latestVersionDouble.intValue();
                    String downloadUrl = document.getString("apk_url");

                    if (latestVersion > currentVersionCode &&
                            downloadUrl != null &&
                            !downloadUrl.isEmpty()) {

                        showUpdateDialog(downloadUrl);
                    }

                })
                .addOnFailureListener(e ->
                        Log.e("AppUpdater",
                                "Update check failed", e));
    }

    private void showUpdateDialog(String url) {

        if (activity.isFinishing() || activity.isDestroyed()) return;

        try {
            new AlertDialog.Builder(activity)
                    .setTitle("New Update Available")
                    .setMessage("A new version of Ordrino is ready. Please update now.")
                    .setCancelable(false)
                    .setPositiveButton("Update",
                            (dialog, which) -> downloadApk(url))
                    .show();

        } catch (Exception e) {
            Log.e("AppUpdater",
                    "Dialog failed (activity probably destroyed)", e);
        }
    }

    private void downloadApk(String url) {

        try {

            DownloadManager.Request request =
                    new DownloadManager.Request(Uri.parse(url));

            request.setTitle("Ordrino Update");
            request.setDescription("Downloading update...");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            // Scoped-storage safe destination
            request.setDestinationInExternalFilesDir(
                    activity,
                    Environment.DIRECTORY_DOWNLOADS,
                    "ordrino_update.apk"
            );

            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);

            DownloadManager manager =
                    (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);

            if (manager == null) {
                Log.e("AppUpdater", "DownloadManager is null");
                return;
            }

            long downloadId = manager.enqueue(request);
            Log.d("AppUpdater", "Download started. ID: " + downloadId);

            // Save download ID for BroadcastReceiver
            activity.getSharedPreferences("update_prefs",
                            Context.MODE_PRIVATE)
                    .edit()
                    .putLong("download_id", downloadId)
                    .apply();


        } catch (Exception e) {
            Log.e("AppUpdater", "Download failed", e);
        }
    }
}
