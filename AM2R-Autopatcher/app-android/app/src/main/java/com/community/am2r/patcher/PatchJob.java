package com.community.am2r.patcher;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** A patch operation owns its edition, temporary files and verified output. */
final class PatchJob extends ContextWrapper implements Runnable {
    interface Listener { void onPatchUpdate(PatchJob job); }
    final String edition;
    final Uri source;
    volatile boolean running = true;
    volatile int progress = -1;
    volatile String message = "Checking your AM2R 1.1 copy…";
    volatile Uri outputUri;
    volatile String outputName;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WeakReference<Listener> listener = new WeakReference<>(null);
    private long lastReport;
    PatchJob(Context context, String edition, Uri source) {
        super(context.getApplicationContext());
        if (!edition.equals("standard") && !edition.equals("dual")) throw new IllegalArgumentException("Unknown edition");
        this.edition = edition; this.source = source;
    }
    void attach(Listener target) { listener = new WeakReference<>(target); notifyListener(); }
    void detach(Listener target) { if (listener.get() == target) listener.clear(); }
    void start() { new Thread(this, "patch-game").start(); }
    private void notifyListener() { main.post(() -> { Listener target = listener.get(); if (target != null) target.onPatchUpdate(this); }); }
    private void progress(long done, long total, String text) {
        progress = total > 1 ? (int)(1000L * done / total) : -1;
        message = text;
        long now = SystemClock.uptimeMillis();
        if (now - lastReport >= 100) { lastReport = now; notifyListener(); }
    }
    @Override public void run() {
        try { runPatch(source); }
        catch (Exception error) {
            progress = 0;
            message = "Could not patch: " + (error.getMessage() == null ? "Please try again." : error.getMessage());
        } finally { running = false; notifyListener(); }
    }
    private static final class PatchException extends Exception {
        PatchException(String message) { super(message); }
    }
    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static String sha256File(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[1 << 20];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return hex(md.digest());
    }

    private void runPatch(Uri zipUri) throws Exception {
        JSONObject manifest;
        try (InputStream in = getAssets().open(edition + "/assembly.json")) {
            byte[] all = readAll(in);
            manifest = new JSONObject(new String(all, "UTF-8"));
        }
        String expectedDataWin = manifest.getString("datawin_sha256");
        JSONObject droidInfo = manifest.getJSONObject("droid");

        File cache = new File(getCacheDir(), "patch-" + java.util.UUID.randomUUID());
        if (!cache.mkdir()) throw new PatchException("Not enough temporary storage.");
        File dataWin = new File(cache, "data.win");
        File deltaFile = new File(cache, "droid.xdelta");
        File droidFile = new File(cache, "game.droid");

        try {
            // 1. Find + verify data.win inside the user's zip (single pass).
            progress(0, 1, "Checking your AM2R 1.1 copy…");
            boolean found = false;
            try (ZipInputStream z = new ZipInputStream(getContentResolver().openInputStream(zipUri))) {
                ZipEntry e;
                while ((e = z.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    String name = e.getName().toLowerCase();
                    if (!name.endsWith("data.win")) continue;
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    try (FileOutputStream out = new FileOutputStream(dataWin)) {
                        byte[] buf = new byte[1 << 20];
                        int n; long inputSize = 0;
                        while ((n = z.read(buf)) > 0) {
                            inputSize += n;
                            if (inputSize > 128L * 1024 * 1024) throw new PatchException("This is not the original 1.1 game data.");
                            md.update(buf, 0, n);
                            out.write(buf, 0, n);
                        }
                    }
                    if (hex(md.digest()).equals(expectedDataWin)) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                throw new PatchException("That zip is not the original AM2R 1.1 release. "
                        + "You need the unmodified 2016 AM2R_11.zip — a Community Updates "
                        + "or modded copy will not work.");
            }

            // 2. Rebuild game data with xdelta3.
            progress(0, 1, "Rebuilding game data from your copy…");
            copyAsset(edition + "/" + droidInfo.getString("xdelta"), deltaFile);
            int r = Xd3.decode(dataWin.getAbsolutePath(), deltaFile.getAbsolutePath(),
                    droidFile.getAbsolutePath(), droidInfo.getLong("size"));
            if (r != 0) throw new PatchException("game data rebuild failed (code " + r + ")");
            if (!sha256File(droidFile).equals(droidInfo.getString("sha256")))
                throw new PatchException("rebuilt game data failed verification");

            // 3. Splice the APK straight into Downloads.
            outputName = manifest.getString("apk_name");
            long total = manifest.getLong("final_size");
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, outputName);
            cv.put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive");
            cv.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri item = getContentResolver().insert(collection, cv);
            if (item == null) throw new PatchException("could not create the output file in Downloads");

            boolean ok = false;
            try {
                MessageDigest finalMd = MessageDigest.getInstance("SHA-256");
                long done = 0;
                JSONArray segs = manifest.getJSONArray("segments");
                try (OutputStream out = getContentResolver().openOutputStream(item);
                     InputStream wrapper = getAssets().open(edition + "/wrapper.bin", android.content.res.AssetManager.ACCESS_STREAMING)) {
                    long wrapperPos = 0;
                    byte[] buf = new byte[1 << 20];
                    for (int i = 0; i < segs.length(); i++) {
                        JSONObject seg = segs.getJSONObject(i);
                        String source = seg.getString("source");
                        if (source.equals("wrapper")) {
                            long off = seg.getLong("offset");
                            if (off != wrapperPos)
                                throw new PatchException("patch data is out of order (corrupt download?)");
                            long remaining = seg.getLong("length");
                            MessageDigest segMd = MessageDigest.getInstance("SHA-256");
                            while (remaining > 0) {
                                int n = wrapper.read(buf, 0, (int) Math.min(buf.length, remaining));
                                if (n <= 0) throw new PatchException("wrapper data truncated (corrupt download?)");
                                segMd.update(buf, 0, n);
                                finalMd.update(buf, 0, n);
                                out.write(buf, 0, n);
                                remaining -= n;
                                wrapperPos += n;
                                done += n;
                                progress(done, total, "Assembling APK…");
                            }
                            if (!hex(segMd.digest()).equals(seg.getString("sha256")))
                                throw new PatchException("patch data failed verification (corrupt download?)");
                        } else if (source.equals("droid")) {
                            try (FileInputStream d = new FileInputStream(droidFile)) {
                                int n;
                                while ((n = d.read(buf)) > 0) {
                                    finalMd.update(buf, 0, n);
                                    out.write(buf, 0, n);
                                    done += n;
                                    progress(done, total, "Assembling APK…");
                                }
                            }
                        } else if (source.equals("zip")) {
                            done += copySource(zipUri, seg, out, finalMd);
                            progress(done, total, "Assembling APK…");
                        } else {
                            throw new PatchException("Unsupported patch data.");
                        }
                    }
                }
                String digest = hex(finalMd.digest());
                if (done != total || !digest.equals(manifest.getString("final_sha256")))
                    throw new PatchException("final APK failed verification — nothing was kept");

                cv.clear();
                cv.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(item, cv, null, null);
                ok = true;
                outputUri = item;
                progress = 1000;
                message = "Ready to install " + (edition.equals("dual") ? "Dual Screen" : "Standard")
                        + ".\nVerified APK saved in Downloads.";
            } finally {
                if (!ok) getContentResolver().delete(item, null, null);
            }
        } finally {
            dataWin.delete();
            deltaFile.delete();
            droidFile.delete();
            cache.delete();
        }
    }

    private long copySource(Uri uri, JSONObject segment, OutputStream out, MessageDigest finalHash) throws Exception {
        String member = segment.getString("path");
        try (ZipInputStream zip = new ZipInputStream(getContentResolver().openInputStream(uri))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !(entry.getName().equals(member) || entry.getName().endsWith("/" + member))) continue;
                long count = 0, expected = segment.getLong("length");
                MessageDigest hash = MessageDigest.getInstance("SHA-256");
                byte[] bytes = new byte[1 << 20]; int size;
                while ((size = zip.read(bytes)) > 0) {
                    count += size;
                    if (count > expected) throw new PatchException("An original file has the wrong size.");
                    hash.update(bytes, 0, size); finalHash.update(bytes, 0, size); out.write(bytes, 0, size);
                }
                if (count != expected || !hex(hash.digest()).equals(segment.getString("sha256")))
                    throw new PatchException("An original file failed verification.");
                return count;
            }
        }
        throw new PatchException("Your ZIP is missing an original game file.");
    }
    private void copyAsset(String name, File dst) throws IOException {
        try (InputStream in = getAssets().open(name); FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[1 << 20];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
