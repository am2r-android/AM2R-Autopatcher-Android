package com.community.am2r.patcher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.system.Os;
import android.system.OsConstants;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import org.json.JSONObject;

/** Installs a verified game through Android's confirmation dialog and displays its result. */
public final class InstallActivity extends Activity {
    static final String RESULT = "com.community.am2r.patcher.INSTALL_RESULT";
    private static final int ALLOW_INSTALL = 2;
    private TextView message;
    private Button copy, share;
    private Uri reportUri;
    private String report = "Preparing installation…";
    private Work work;
    private boolean finished;
    private boolean waitingForPermission;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        message = new TextView(this); message.setTextSize(18); message.setTextIsSelectable(true);
        layout.addView(message);
        copy = new Button(this); copy.setText("COPY DETAILS");
        copy.setOnClickListener(v -> ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText("AM2R installation", report)));
        layout.addView(copy);
        share = new Button(this); share.setText("SHARE REPORT");
        share.setOnClickListener(v -> {
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, reportUri).putExtra(Intent.EXTRA_TEXT, InstallReports.ISSUES)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newRawUri("AM2R installation report", reportUri));
            try { startActivity(Intent.createChooser(send, InstallReports.CAPTION)); }
            catch (RuntimeException error) { message.setText(report + "\n\nOpen the report from Downloads or use COPY DETAILS."); }
        });
        layout.addView(share);
        Button close = new Button(this); close.setText("BACK TO PATCHER");
        close.setOnClickListener(v -> finish()); layout.addView(close);
        ScrollView scroll = new ScrollView(this); scroll.addView(layout); setContentView(scroll);
        if (saved != null) {
            report = saved.getString("report", report); finished = saved.getBoolean("finished");
            waitingForPermission = saved.getBoolean("waitingForPermission");
            String savedUri = saved.getString("reportUri");
            if (savedUri != null) reportUri = Uri.parse(savedUri);
        }
        work = (Work)getLastNonConfigurationInstance();
        if (work != null) work.owner = new WeakReference<>(this);
        show();
        if (work != null && work.failureMessage != null && !finished)
            failure(work.failureMessage + deviceDetails());
        if (RESULT.equals(getIntent().getAction())) { if (saved == null) receive(getIntent()); }
        else if (saved == null && work == null) {
            if (getPackageManager().canRequestPackageInstalls()) begin();
            else {
                waitingForPermission = true;
                report = "Allow this patcher to install apps, then return here."; show();
                try { startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())), ALLOW_INSTALL); }
                catch (RuntimeException error) { complete("Open Android Settings and allow this patcher to install apps. Your APK is still in Downloads."); }
            }
        } else if (work == null && !finished && !waitingForPermission && saved != null) {
            complete("Installation was interrupted. Return to the patcher and try again; your APK is still in Downloads.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == ALLOW_INSTALL) {
            waitingForPermission = false;
            if (getPackageManager().canRequestPackageInstalls()) begin();
            else complete("Install permission was not granted. Your APK is still in Downloads.");
        }
    }

    private void begin() {
        if (work != null) return;
        String edition = getIntent().getStringExtra("edition");
        Uri uri = getIntent().getData();
        if (uri == null || !("standard".equals(edition) || "dual".equals(edition))) {
            complete("Choose an edition and patch your original ZIP first."); return;
        }
        finished = false; report = "Preparing installation…"; show();
        work = new Work(this, uri, edition); new Thread(work, "install-game").start();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (RESULT.equals(intent.getAction())) { setIntent(intent); receive(intent); }
    }

    private void receive(Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm == null) { complete("Android did not provide an installation confirmation. Your APK is still in Downloads."); return; }
            report = "Confirm installation in Android's dialog."; show();
            // Consume this callback so rotating the display cannot launch confirmation twice.
            getIntent().removeExtra(Intent.EXTRA_INTENT);
            try { startActivity(confirm); }
            catch (RuntimeException error) { complete("Android could not open its installer. Your APK is still in Downloads."); }
            return;
        }
        String detail = InstallReport.summary(status);
        if (status != PackageInstaller.STATUS_SUCCESS && status != PackageInstaller.STATUS_FAILURE_ABORTED) {
            detail += "\n\nAndroid status: " + status
                    + "\nReason: " + InstallReport.failureCode(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
                    + deviceDetails();
            failure(detail); return;
        }
        complete(detail);
    }

    private void complete(String text) { finished = true; report = text; show(); }
    private String deviceDetails() {
        return "\nEdition: " + getIntent().getStringExtra("edition")
                + "\nPatcher: " + BuildConfig.VERSION_NAME
                + "\nDevice type: " + Build.MANUFACTURER + " " + Build.MODEL
                + "\nAndroid: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\nCPU support: " + String.join(", ", Build.SUPPORTED_ABIS)
                + "\nMemory page size: " + Os.sysconf(OsConstants._SC_PAGESIZE) + " bytes";
    }
    private void failure(String text) {
        String body = InstallReports.CAPTION + "\n" + InstallReports.ISSUES + "\n\n" + text;
        try {
            reportUri = InstallReports.save(this, body);
            complete(body + "\n\nReport saved in Downloads. Attach the AM2R-install-report text file to your issue.");
        } catch (Exception error) {
            complete(body + "\n\nThe report could not be saved. Use COPY DETAILS instead.");
        }
    }
    private void show() {
        message.setText(report); copy.setVisibility(finished ? View.VISIBLE : View.GONE);
        share.setVisibility(reportUri != null ? View.VISIBLE : View.GONE);
    }
    @Override public Object onRetainNonConfigurationInstance() { return work; }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("report", report); out.putBoolean("finished", finished); super.onSaveInstanceState(out);
        out.putBoolean("waitingForPermission", waitingForPermission);
        if (reportUri != null) out.putString("reportUri", reportUri.toString());
    }
    @Override protected void onDestroy() {
        if (work != null) { work.owner.clear(); if (isFinishing()) work.cancelled = true; }
        super.onDestroy();
    }

    // Copy and recheck the APK off the UI thread before asking Android to install it.
    private static final class Work implements Runnable {
        final Context context;
        final Uri uri;
        final String edition;
        volatile WeakReference<InstallActivity> owner;
        volatile boolean cancelled;
        volatile String failureMessage;
        Work(InstallActivity activity, Uri uri, String edition) {
            context = activity.getApplicationContext(); owner = new WeakReference<>(activity);
            this.uri = uri; this.edition = edition;
        }
        @Override public void run() {
            PackageInstaller installer = context.getPackageManager().getPackageInstaller();
            int sessionId = -1;
            boolean committed = false;
            try {
                JSONObject manifest;
                try (InputStream in = context.getAssets().open(edition + "/assembly.json")) {
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
                    manifest = new JSONObject(bytes.toString("UTF-8"));
                }
                long size = manifest.getLong("final_size");
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(edition.equals("dual") ? BuildConfig.GAME_DUAL_PACKAGE : BuildConfig.GAME_STANDARD_PACKAGE);
                params.setSize(size);
                if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
                sessionId = installer.createSession(params);
                try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    long copied = 0;
                    try (InputStream in = context.getContentResolver().openInputStream(uri);
                         OutputStream out = session.openWrite("base.apk", 0, size)) {
                        if (in == null) throw new java.io.IOException("The saved APK could not be opened.");
                        byte[] buffer = new byte[1024 * 1024]; int count;
                        while ((count = in.read(buffer)) != -1) {
                            if (cancelled) return;
                            copied += count;
                            if (copied > size) throw new java.io.IOException("The saved APK changed. Patch your original ZIP again.");
                            digest.update(buffer, 0, count); out.write(buffer, 0, count);
                        }
                        session.fsync(out);
                    }
                    StringBuilder sha = new StringBuilder();
                    for (byte value : digest.digest()) sha.append(String.format("%02x", value));
                    if (copied != size || !sha.toString().equals(manifest.getString("final_sha256")))
                        throw new java.io.IOException("The saved APK changed. Patch your original ZIP again.");
                    if (cancelled) return;
                    Intent callback = new Intent(context, InstallActivity.class).setAction(RESULT)
                            .setData(Uri.parse("am2r-install:" + sessionId)).putExtra("edition", edition)
                            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                    if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
                    Bundle options = null;
                    if (Build.VERSION.SDK_INT >= 35) options = ActivityOptions.makeBasic()
                            .setPendingIntentCreatorBackgroundActivityStartMode(
                                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle();
                    PendingIntent result = PendingIntent.getActivity(context, sessionId, callback, flags, options);
                    session.commit(result.getIntentSender()); committed = true;
                }
            } catch (Exception error) {
                // Retain preparation failures if the display is recreated before the UI callback runs.
                failureMessage = "Could not prepare installation. Check that the APK is still in Downloads and that internal storage is available.\n\n"
                        + "If the file was changed, patch your original ZIP again. (" + error.getClass().getSimpleName() + ")";
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    InstallActivity activity = owner.get();
                    if (activity != null && !activity.finished && !cancelled)
                        activity.failure(failureMessage + activity.deviceDetails());
                });
            } finally {
                if (!committed && sessionId >= 0) {
                    try { installer.abandonSession(sessionId); } catch (RuntimeException ignored) { }
                }
            }
        }
    }
}
