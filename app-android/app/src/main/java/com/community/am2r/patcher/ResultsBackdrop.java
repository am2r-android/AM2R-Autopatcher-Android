package com.community.am2r.patcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

/** Original results-screen artwork, with whole-pixel enlargement. */
final class ResultsBackdrop extends View {
    private final Bitmap stars, samus;
    private final Paint paint = new Paint();
    private float introProgress = 1;
    ResultsBackdrop(Context context) {
        super(context);
        // Load artwork at its original pixel size so the draw routine controls its enlargement.
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inScaled = false;
        stars = BitmapFactory.decodeResource(getResources(), R.drawable.ending_stars, options);
        samus = BitmapFactory.decodeResource(getResources(), R.drawable.ending_samus, options);
        paint.setFilterBitmap(false); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void setIntroProgress(float value) { introProgress = value; invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        int width = getWidth(), height = getHeight();
        // Enlarge the star background enough to cover the view throughout its intro pan.
        int pan = Math.round(width * .16f);
        int scale = Math.max(1, (int)Math.ceil(Math.max((float)(width + pan) / stars.getWidth(), (float)height / stars.getHeight())));
        int x = (width - stars.getWidth() * scale) / 2, y = (height - stars.getHeight() * scale) / 2;
        x += Math.round(Math.min(pan, -x) * (1 - introProgress));
        canvas.drawBitmap(stars, null, new Rect(x, y, x + stars.getWidth() * scale, y + stars.getHeight() * scale), paint);
        // Fill the artwork column with a larger portrait while keeping the original pixel proportions.
        int artWidth = Math.round(width * .40f);
        scale = Math.max(1, Math.min(height / samus.getHeight(), (int)Math.ceil((float)artWidth / samus.getWidth())));
        int target = (artWidth - samus.getWidth() * scale) / 2;
        x = Math.round(target + (width - target) * (1 - introProgress));
        y = height - samus.getHeight() * scale;
        canvas.drawBitmap(samus, null, new Rect(x, y, x + samus.getWidth() * scale, height), paint);
    }
}
