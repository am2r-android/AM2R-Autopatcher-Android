package com.community.am2r.patcher;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class PatcherActivity extends Activity {

    private static final int PICK_ZIP = 1;

    private TextView status;
    private ProgressBar bar;
    private Button pickButton;
    private Button installButton;
    private Uri outputUri;
    private String outputName;

    // Greens sampled from the launcher icon's pixel-art palette.
    private static final int GREEN_MID = 0xFF79BC6D;
    private static final int GREEN_DARK = 0xFF1C3325;
    private static final int GREEN_PALE = 0xFFE4F9D0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad * 2, pad * 2, pad * 2, pad * 2);
        root.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("AM2R for Android");
        title.setTextSize(26);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(GREEN_PALE);
        title.setShadowLayer(8, 0, 2, 0xCC000000);
        root.addView(title);

        TextView blurb = new TextView(this);
        blurb.setText("\nSelect your copy of the original AM2R 1.1 release (AM2R_11.zip). "
                + "It will be patched into the Android port, right here on your phone. "
                + "Your zip is not modified.\n");
        blurb.setTextSize(15);
        blurb.setGravity(Gravity.CENTER);
        blurb.setTextColor(GREEN_PALE);
        blurb.setShadowLayer(6, 0, 1, 0xCC000000);
        root.addView(blurb);

        pickButton = new Button(this);
        pickButton.setText("Choose AM2R_11.zip…");
        pickButton.setAllCaps(false);
        pickButton.setTypeface(null, Typeface.BOLD);
        pickButton.setBackgroundTintList(ColorStateList.valueOf(GREEN_MID));
        pickButton.setTextColor(GREEN_DARK);
        pickButton.setOnClickListener(v -> pickZip());
        root.addView(pickButton);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setVisibility(View.INVISIBLE);
        bar.setProgressTintList(ColorStateList.valueOf(GREEN_MID));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(GREEN_DARK));
        root.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setText("Waiting for your AM2R_11.zip…");
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, pad, 0, 0);
        status.setTextColor(GREEN_PALE);
        status.setShadowLayer(6, 0, 1, 0xCC000000);
        root.addView(status);

        installButton = new Button(this);
        installButton.setText("Install AM2R");
        installButton.setAllCaps(false);
        installButton.setTypeface(null, Typeface.BOLD);
        installButton.setBackgroundTintList(ColorStateList.valueOf(GREEN_MID));
        installButton.setTextColor(GREEN_DARK);
        installButton.setVisibility(View.GONE);
        installButton.setOnClickListener(v -> installApk());
        root.addView(installButton);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);

        FrameLayout frame = new FrameLayout(this);
        ImageView background = new ImageView(this);
        background.setImageResource(R.drawable.bg_samus);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(background, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View scrim = new View(this);
        scrim.setBackgroundColor(0x59000000);
        frame.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.addView(scroll);
        setContentView(frame);
    }

    private void pickZip() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/octet-stream"});
        startActivityForResult(i, PICK_ZIP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_ZIP || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri zipUri = data.getData();
        pickButton.setEnabled(false);
        installButton.setVisibility(View.GONE);
        bar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                runPatch(zipUri);
            } catch (PatchException e) {
                fail(e.getMessage());
            } catch (Exception e) {
                fail("Unexpected error: " + e);
            }
        }).start();
    }

    private static class PatchException extends Exception {
        PatchException(String msg) { super(msg); }
    }

    private void progress(long done, long total, String msg) {
        runOnUiThread(() -> {
            bar.setProgress((int) (1000L * done / Math.max(total, 1)));
            status.setText(msg);
        });
    }

    private void fail(String msg) {
        runOnUiThread(() -> {
            status.setText("Failed: " + msg);
            pickButton.setEnabled(true);
            bar.setVisibility(View.INVISIBLE);
        });
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
        try (InputStream in = getAssets().open("assembly.json")) {
            byte[] all = readAll(in);
            manifest = new JSONObject(new String(all, "UTF-8"));
        }
        String expectedDataWin = manifest.getString("datawin_sha256");
        JSONObject droidInfo = manifest.getJSONObject("droid");

        File cache = getCacheDir();
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
                        int n;
                        while ((n = z.read(buf)) > 0) {
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
            copyAsset(droidInfo.getString("xdelta"), deltaFile);
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
                     InputStream wrapper = getAssets().open("wrapper.bin", android.content.res.AssetManager.ACCESS_STREAMING)) {
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
                        } else {
                            throw new PatchException("this patch data needs a newer patcher (segment: " + source + ")");
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
                runOnUiThread(() -> {
                    bar.setProgress(1000);
                    status.setText("Done! " + outputName + " is in your Downloads.\n\n"
                            + "Verified byte-for-byte against the official release.\n"
                            + "SHA-256: " + digest);
                    pickButton.setEnabled(true);
                    installButton.setVisibility(View.VISIBLE);
                });
            } finally {
                if (!ok) getContentResolver().delete(item, null, null);
            }
        } finally {
            dataWin.delete();
            deltaFile.delete();
            droidFile.delete();
        }
    }

    private void installApk() {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(outputUri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(i);
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
