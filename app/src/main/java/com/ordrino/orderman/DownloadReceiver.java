package com.ordrino.orderman;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;

public class DownloadReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction()))
            return;

        long completedId = intent.getLongExtra(
                DownloadManager.EXTRA_DOWNLOAD_ID, -1);

        long savedId = context.getSharedPreferences(
                        "update_prefs", Context.MODE_PRIVATE)
                .getLong("download_id", -1);

        if (completedId != savedId) return;

        DownloadManager manager =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

        if (manager == null) return;

        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(completedId);

        try (android.database.Cursor cursor = manager.query(query)) {

            if (cursor != null && cursor.moveToFirst()) {

                int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int status = cursor.getInt(statusIndex);

                if (status == DownloadManager.STATUS_SUCCESSFUL) {

                    String uriString = cursor.getString(
                            cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));

                    Uri fileUri = Uri.parse(uriString);

                    File file = new File(fileUri.getPath());

                    Uri contentUri = FileProvider.getUriForFile(
                            context,
                            context.getPackageName() + ".provider",
                            file);

                    Intent install = new Intent(Intent.ACTION_VIEW);
                    install.setDataAndType(contentUri,
                            "application/vnd.android.package-archive");
                    install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    context.startActivity(install);
                }
            }

        } catch (Exception e) {
            Log.e("DownloadReceiver", "Install failed", e);
        }
    }
}
