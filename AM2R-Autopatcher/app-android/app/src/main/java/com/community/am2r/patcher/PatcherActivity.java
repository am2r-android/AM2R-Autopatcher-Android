package com.community.am2r.patcher;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.util.TypedValue;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

public final class PatcherActivity extends Activity implements PatchJob.Listener {
    private static final int PICK_ZIP = 1;
    private static final int ACCENT = 0xff5be1ed, PALE = 0xffedf2ff;
    private Typeface menuFont, smallFont;
    private ResultsBackdrop backdrop;
    private View menuColumns;
    private ValueAnimator intro;
    private TextView status, editionHelp;
    private ProgressBar bar;
    private Button standard, dual, pick, install;
    private String edition = "standard";
    private PatchJob job;
    private Uri outputUri;
    private String outputName;
    private int navDirection;
    private long navTime;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        menuFont = getResources().getFont(R.font.am2r_menu);
        smallFont = getResources().getFont(R.font.am2r_small);
        edition = getPreferences(MODE_PRIVATE).getString("edition", "standard");
        if (!edition.equals("dual")) edition = "standard";
        if (saved != null) {
            edition = saved.getString("edition", edition);
            outputName = saved.getString("outputName");
            String uri = saved.getString("outputUri");
            if (uri != null) outputUri = Uri.parse(uri);
        }
        job = (PatchJob) getLastNonConfigurationInstance();
        buildLayout();
        if (job != null) { edition = job.edition; job.attach(this); }
        refreshEdition(); immersive();
        if (saved == null && job == null) playIntro();
    }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size, int color) {
        TextView v = new TextView(this);
        v.setText(text); v.setTextColor(color); gameFont(v, size, size > 12);
        return v;
    }
    private void gameFont(TextView view, int size, boolean menu) {
        int base = menu ? 13 : 8;
        int scale = Math.max(1, Math.round(size * getResources().getDisplayMetrics().scaledDensity / base));
        view.setTypeface(menu ? menuFont : smallFont);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, base * scale);
        view.setIncludeFontPadding(false);
        view.setPaintFlags(view.getPaintFlags() & ~Paint.ANTI_ALIAS_FLAG & ~Paint.SUBPIXEL_TEXT_FLAG);
    }
    private GradientDrawable panel(int top, int bottom, int border) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        d.setStroke(dp(1), border); return d;
    }
    private Button button(String text) {
        Button v = new Button(this);
        v.setText(text); v.setTextColor(PALE); gameFont(v, 21, true);
        v.setGravity(Gravity.CENTER); v.setAllCaps(false);
        v.setMinHeight(dp(48)); v.setMinimumHeight(dp(48)); v.setPadding(dp(10), dp(8), dp(10), dp(8));
        v.setStateListAnimator(null);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_pressed}, panel(0xff434277, 0xff182f4f, ACCENT));
        bg.addState(new int[]{android.R.attr.state_focused}, panel(0xff35305d, 0xff14273b, ACCENT));
        bg.addState(new int[]{android.R.attr.state_selected}, panel(0xff29244e, 0xff102b3c, ACCENT));
        bg.addState(new int[]{}, panel(0xee151224, 0xee070a14, 0xff655e8f));
        v.setBackground(bg); v.setFocusable(true); v.setId(View.generateViewId()); return v;
    }
    private void buildLayout() {
        FrameLayout frame = new FrameLayout(this);
        backdrop = new ResultsBackdrop(this);
        frame.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout columns = new LinearLayout(this);
        menuColumns = columns;
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.addView(new View(this), new LinearLayout.LayoutParams(0, -1, .40f));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        columns.addView(scroll, new LinearLayout.LayoutParams(0, -1, .60f));
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL); menu.setGravity(Gravity.CENTER_VERTICAL);
        menu.setPadding(dp(16), dp(18), dp(24), dp(18)); menu.setBackgroundColor(0xb9070316);
        scroll.addView(menu, new ScrollView.LayoutParams(-1, -1));
        menu.addView(label("AM2R", 30, PALE)); menu.addView(label("ANDROID AUTOPATCHER", 12, ACCENT));
        TextView heading = label("CHOOSE YOUR EDITION", 12, PALE);
        heading.setPadding(0, dp(16), 0, dp(8)); menu.addView(heading);
        LinearLayout editions = new LinearLayout(this);
        standard = button("STANDARD"); dual = button("DUAL SCREEN");
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, -2, 1); first.rightMargin = dp(6);
        editions.addView(standard, first); editions.addView(dual, new LinearLayout.LayoutParams(0, -2, 1));
        menu.addView(editions);
        standard.setOnClickListener(v -> selectEdition("standard")); dual.setOnClickListener(v -> selectEdition("dual"));
        editionHelp = label("", 12, 0xffcbc5e3); editionHelp.setMinLines(2);
        editionHelp.setPadding(0, dp(8), 0, dp(12)); menu.addView(editionHelp);
        pick = button("CHOOSE AM2R 1.1 ZIP"); pick.setOnClickListener(v -> pickZip());
        menu.addView(pick, new LinearLayout.LayoutParams(-1, -2));
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); bar.setMax(1000);
        bar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xff26223e));
        LinearLayout.LayoutParams meter = new LinearLayout.LayoutParams(-1, dp(8)); meter.topMargin = dp(12);
        menu.addView(bar, meter);
        status = label("Select your original Windows 1.1 ZIP.\nYour source file stays unchanged.", 12, PALE);
        status.setPadding(0, dp(9), 0, dp(12)); menu.addView(status);
        install = button("INSTALL AM2R"); install.setOnClickListener(v -> installApk());
        install.setVisibility(outputUri == null ? View.GONE : View.VISIBLE);
        menu.addView(install, new LinearLayout.LayoutParams(-1, -2));
        frame.addView(columns, new FrameLayout.LayoutParams(-1, -1)); setContentView(frame);
        (edition.equals("dual") ? dual : standard).requestFocus(); refreshEdition();
    }
    private void selectEdition(String next) {
        if (job != null && job.running) return;
        if (!edition.equals(next)) {
            edition = next; outputUri = null; outputName = null; job = null;
            install.setVisibility(View.GONE); bar.setProgress(0);
            status.setText("Select your original Windows 1.1 ZIP.\nYour source file stays unchanged.");
            getPreferences(MODE_PRIVATE).edit().putString("edition", edition).apply();
        }
        refreshEdition();
    }
    private void refreshEdition() {
        standard.setSelected(edition.equals("standard")); dual.setSelected(edition.equals("dual"));
        editionHelp.setText(edition.equals("dual") ? "For AYN Thor, RG DS, AYANEO DS and Retroid Duo / Lite."
                : "For phones, tablets and single-screen handhelds.");
        boolean busy = job != null && job.running;
        for (Button b : new Button[]{standard, dual, pick}) { b.setEnabled(!busy); b.setAlpha(busy ? .5f : 1); }
        install.setText(edition.equals("dual") ? "INSTALL DUAL SCREEN" : "INSTALL STANDARD");
    }
    private void pickZip() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/octet-stream", "application/x-zip-compressed"});
        startActivityForResult(i, PICK_ZIP);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_ZIP || result != RESULT_OK || data == null || data.getData() == null) return;
        if (job != null && job.running) return;
        outputUri = null; outputName = null; install.setVisibility(View.GONE);
        job = new PatchJob(getApplicationContext(), edition, data.getData()); job.attach(this); refreshEdition(); job.start();
    }
    @Override public void onPatchUpdate(PatchJob updated) {
        if (isFinishing() || job != updated) return;
        bar.setIndeterminate(updated.running && updated.progress < 0);
        if (updated.progress >= 0) bar.setProgress(updated.progress);
        status.setText(updated.message); outputUri = updated.outputUri; outputName = updated.outputName;
        install.setVisibility(outputUri == null ? View.GONE : View.VISIBLE); refreshEdition();
    }
    private void installApk() {
        if (outputUri == null) return;
        Intent i = new Intent(Intent.ACTION_VIEW); i.setDataAndType(outputUri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(i);
    }
    @Override public Object onRetainNonConfigurationInstance() { return job; }
    @Override protected void onPause() { finishIntro(); super.onPause(); }
    @Override protected void onDestroy() { finishIntro(); if (job != null) job.detach(this); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("edition", edition); out.putString("outputName", outputName);
        if (outputUri != null) out.putString("outputUri", outputUri.toString()); super.onSaveInstanceState(out);
    }
    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    @Override public void onWindowFocusChanged(boolean focus) { super.onWindowFocusChanged(focus); if (focus) immersive(); }
    private void playIntro() {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        backdrop.setIntroProgress(0); menuColumns.setAlpha(0);
        menuColumns.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        intro = ValueAnimator.ofFloat(0, 1); intro.setDuration(1900);
        intro.setInterpolator(new LinearInterpolator());
        intro.addUpdateListener(animation -> {
            float progress = (float)animation.getAnimatedValue();
            float slide = Math.min(1, progress / .78f);
            backdrop.setIntroProgress(1 - (float)Math.pow(1 - slide, 3));
            menuColumns.setAlpha(Math.max(0, (progress - .78f) / .22f));
            if (progress >= 1) menuColumns.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        });
        menuColumns.post(() -> { if (intro != null) intro.start(); });
    }
    private void finishIntro() {
        if (intro == null) return;
        intro.cancel(); intro = null; backdrop.setIntroProgress(1); menuColumns.setAlpha(1);
        menuColumns.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (intro != null && intro.isRunning()) {
            if (event.getAction() == MotionEvent.ACTION_UP) finishIntro();
            return true;
        }
        return super.dispatchTouchEvent(event);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (intro != null && intro.isRunning()) {
            if (event.getAction() == KeyEvent.ACTION_UP) finishIntro();
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_A) {
            if (event.getAction() == KeyEvent.ACTION_UP && getCurrentFocus() != null) getCurrentFocus().performClick(); return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean onGenericMotionEvent(MotionEvent e) {
        if ((e.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            float x = e.getAxisValue(MotionEvent.AXIS_HAT_X), y = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
            if (Math.abs(x) < .5f) x = e.getAxisValue(MotionEvent.AXIS_X);
            if (Math.abs(y) < .5f) y = e.getAxisValue(MotionEvent.AXIS_Y);
            int dir = Math.abs(y) > .5f ? (y < 0 ? View.FOCUS_UP : View.FOCUS_DOWN)
                    : Math.abs(x) > .5f ? (x < 0 ? View.FOCUS_LEFT : View.FOCUS_RIGHT) : 0;
            long now = SystemClock.uptimeMillis();
            if (dir != 0 && (dir != navDirection || now - navTime > 240)) {
                View focus = getCurrentFocus(), next = focus == null ? pick : focus.focusSearch(dir);
                if (next != null) next.requestFocus(); navTime = now;
            }
            navDirection = dir; return true;
        }
        return super.onGenericMotionEvent(e);
    }
}
