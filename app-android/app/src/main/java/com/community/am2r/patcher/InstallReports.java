package com.community.am2r.patcher;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class InstallReports {
    static final String CAPTION = "share this to github issues page";
    static final String ISSUES = "https://github.com/am2r-android/AM2R-Autopatcher-Android/issues";

    // Expose only a complete report in Downloads, ready for the player to attach to an issue.
    static Uri save(Context context, String report) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, "AM2R-install-report-" + System.currentTimeMillis() + ".txt");
        values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri item = context.getContentResolver().insert(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
        if (item == null) throw new IOException("Could not create report");
        boolean complete = false;
        try {
            try (OutputStream out = context.getContentResolver().openOutputStream(item)) {
                if (out == null) throw new IOException("Could not write report");
                out.write(report.getBytes(StandardCharsets.UTF_8));
            }
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0);
            if (context.getContentResolver().update(item, values, null, null) != 1)
                throw new IOException("Could not finish report");
            complete = true;
            return item;
        } finally {
            if (!complete) context.getContentResolver().delete(item, null, null);
        }
    }
}
