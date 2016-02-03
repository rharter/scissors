/*
 * Copyright (C) 2015 Lyft, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lyft.android.scissors;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ImageView;
import com.lyft.android.scissors.CropViewExtensions.CropRequest;
import com.lyft.android.scissors.CropViewExtensions.LoadRequest;
import java.io.File;
import java.io.OutputStream;

/**
 * An {@link ImageView} with a fixed viewport and cropping capabilities.
 */
public class CropView extends ImageView {

    public static final float DEFAULT_VIEWPORT_RATIO = 0f;
    public static final float DEFAULT_MAXIMUM_SCALE = 10f;
    public static final float DEFAULT_MINIMUM_SCALE = 0f;
    public static final int DEFAULT_IMAGE_QUALITY = 100;
    public static final int DEFAULT_VIEWPORT_OVERLAY_PADDING = 0;
    public static final int DEFAULT_VIEWPORT_OVERLAY_COLOR = 0xC8000000; // Black with 200 alpha
    public static final boolean DEFAULT_SNAPPING_ENABLED = true;

    private static final int MAX_TOUCH_POINTS = 2;
    private TouchManager touchManager;

    private Paint viewportPaint = new Paint();
    private Paint bitmapPaint = new Paint();

    private Bitmap bitmap;
    private Matrix transform = new Matrix();
    private Extensions extensions;

    public CropView(Context context) {
        super(context);
        initCropView(context, null);
    }

    public CropView(Context context, AttributeSet attrs) {
        super(context, attrs);

        initCropView(context, attrs);
    }

    void initCropView(Context context, AttributeSet attrs) {

        TypedArray attributes = context.obtainStyledAttributes(
            attrs,
            R.styleable.CropView);

        final float aspectRatio = attributes.getFloat(R.styleable.CropView_cropviewViewportRatio,
                DEFAULT_VIEWPORT_RATIO);

        final float maxScale = attributes.getFloat(R.styleable.CropView_cropviewMaxScale,
                DEFAULT_MAXIMUM_SCALE);

        final float minScale = attributes.getFloat(R.styleable.CropView_cropviewMinScale,
                DEFAULT_MINIMUM_SCALE);

        final int overlayColor = attributes.getColor(
                R.styleable.CropView_cropviewViewportOverlayColor,
                DEFAULT_VIEWPORT_OVERLAY_COLOR);

        final int overlayPadding = attributes.getDimensionPixelSize(
                R.styleable.CropView_cropviewViewportOverlayPadding,
                DEFAULT_VIEWPORT_OVERLAY_PADDING);

        final boolean snappingEnabled = attributes.getBoolean(
                R.styleable.CropView_cropviewSnappingEnabled,
                DEFAULT_SNAPPING_ENABLED);
        attributes.recycle();

        touchManager = new TouchManager(MAX_TOUCH_POINTS, aspectRatio, overlayPadding,
            snappingEnabled, minScale, maxScale);

        bitmapPaint.setFilterBitmap(true);
        viewportPaint.setColor(overlayColor);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (bitmap == null) {
            return;
        }

        drawBitmap(canvas);
        drawOverlay(canvas);
    }

    private void drawBitmap(Canvas canvas) {
        transform.reset();
        touchManager.applyPositioningAndScale(transform);

        canvas.drawBitmap(bitmap, transform, bitmapPaint);
    }

    private void drawOverlay(Canvas canvas) {
        final int viewportWidth = touchManager.getViewportWidth();
        final int viewportHeight = touchManager.getViewportHeight();
        final int left = (getWidth() - viewportWidth) / 2;
        final int top = (getHeight() - viewportHeight) / 2;

        canvas.drawRect(0, top, left, getHeight() - top, viewportPaint);
        canvas.drawRect(0, 0, getWidth(), top, viewportPaint);
        canvas.drawRect(getWidth() - left, top, getWidth(), getHeight() - top, viewportPaint);
        canvas.drawRect(0, getHeight() - top, getWidth(), getHeight(), viewportPaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetTouchManager();
    }

    public float getPositionX() {
        return touchManager.getPosition().getX();
    }

    public float getPositionY() {
        return touchManager.getPosition().getY();
    }

    /**
     * Returns the aspect ratio of the crop rect and overlay.
     *
     * @return The current aspect ratio.
     */
    public float getAspectRatio() {
        return touchManager.getAspectRatio();
    }

    /**
     * Returns the effective aspect ratio of the crop rect and overlay, which
     * equals the native aspect ratio of the image if the set aspect ratio is 0.
     *
     * @return The effective aspect ratio.
     */
    public float getEffectiveAspectRatio() {
        float aspect = touchManager.getAspectRatio();
        if (Float.compare(aspect, 0f) == 0) {
            aspect = (float) touchManager.getViewportWidth() / touchManager.getViewportHeight();
        }
        return aspect;
    }

    /**
     * Sets the aspect ratio of the crop rect and overlay.
     *
     * @param ratio The ratio of the crop rect, 0 to match
     * the image's native ratio.
     */
    public void setAspectRatio(float ratio) {
        touchManager.setAspectRatio(ratio);
        resetTouchManager();
        invalidate();
    }

    /**
     * The maximum that the image can be zoomed.
     * @return The maximum amount of scaling.
     */
    public float getMaxScale() {
        return touchManager.getMaximumScale();
    }

    /**
     * Sets that maximum amount that the image can be zoomed.
     * @param maxScale The maximum amount of scaling.
     */
    public void setMaxScale(float maxScale) {
        touchManager.setMaximumScale(maxScale);
    }

    /**
     * The minimum that the image can be zoomed.
     * @return The minimum amount of scaling.
     */
    public float getMinScale() {
        return touchManager.getMinimumScale();
    }

    /**
     * Sets the minimum amount that the image can be zoomed.
     * @param minScale The minimum amount of scaling.
     */
    public void setMinScale(float minScale) {
        touchManager.setMinimumScale(minScale);
        resetTouchManager();
        invalidate();
    }

    /**
     * The current scale value of the image.
     * @return The current scale value of the image.
     */
    public float getScale() {
        return touchManager.getScale();
    }

    /**
     * Sets the current scale value of the image.
     * @param scale The scale to set.
     */
    public void setScale(float scale) {
        touchManager.setScale(scale);
    }

    /**
     * The amount of padding of the overlay around all sides
     * of the view, regardless of aspect ratio.
     * @return The padding of the overlay.
     */
    public int getViewportOverlayPadding() {
        return touchManager.getOverlayPadding();
    }

    /**
     * Sets the amount of padding for the overlay around all
     * sides of the view, regardless of aspect ratio.
     * @param padding The overlay padding to set.
     */
    public void setViewportOverlayPadding(int padding) {
        touchManager.setOverlayPadding(padding);
        resetTouchManager();
        invalidate();
    }

    /**
     * Whether or not snapping is enabled.
     *
     * <p>If true, the image will always follow the drag,
     * and then snap back into a valid position once the
     * user has released. Otherwise, the user will be unable
     * to drag the image beyond the bounds of the viewport.
     * @return True if snapping is enabled, false otherwise.
     */
    public boolean isSnappingEnabled() {
        return touchManager.isSnappingEnabled();
    }

    /**
     * Turns on or off snapping.
     *
     * <p>If true, the image will always follow the drag,
     * and then snap back into a valid position once the
     * user has released. Otherwise, the user will be unable
     * to drag the image beyond the bounds of the viewport.
     * @param enabled True to enable snapping, false otherwise.
     */
    public void setSnappingEnabled(boolean enabled) {
        touchManager.setSnappingEnabled(enabled);
    }

    @Override
    public void setImageResource(@DrawableRes int resId) {
        final Bitmap bitmap = resId > 0
                ? BitmapFactory.decodeResource(getResources(), resId)
                : null;
        setImageBitmap(bitmap);
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        final Bitmap bitmap;
        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            bitmap = bitmapDrawable.getBitmap();
        } else if (drawable != null) {
            bitmap = Utils.asBitmap(drawable, getWidth(), getHeight());
        } else {
            bitmap = null;
        }

        setImageBitmap(bitmap);
    }

    @Override
    public void setImageURI(@Nullable Uri uri) {
        extensions().load(uri);
    }

    @Override
    public void setImageBitmap(@Nullable Bitmap bitmap) {
        this.bitmap = bitmap;
        resetTouchManager();
        invalidate();
    }

    /**
     * @return Current working Bitmap or <code>null</code> if none has been set yet.
     */
    @Nullable
    public Bitmap getImageBitmap() {
        return bitmap;
    }

    private void resetTouchManager() {
        final boolean invalidBitmap = bitmap == null;
        final int bitmapWidth = invalidBitmap ? 0 : bitmap.getWidth();
        final int bitmapHeight = invalidBitmap ? 0 : bitmap.getHeight();
        touchManager.resetFor(bitmapWidth, bitmapHeight, getWidth(), getHeight());
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        super.dispatchTouchEvent(event);
        if (!isEnabled()) {
            return false;
        }
        touchManager.onEvent(event);
        invalidate();
        return true;
    }

    /**
     * Performs synchronous image cropping based on configuration.
     *
     * @return A {@link Bitmap} cropped based on viewport and user panning and zooming or <code>null</code> if no {@link Bitmap} has been
     * provided.
     */
    @Nullable
    public Bitmap crop() {
        if (bitmap == null) {
            return null;
        }

        final Bitmap src = bitmap;
        final Bitmap.Config srcConfig = src.getConfig();
        final Bitmap.Config config = srcConfig == null ? Bitmap.Config.ARGB_8888 : srcConfig;
        final int viewportHeight = touchManager.getViewportHeight();
        final int viewportWidth = touchManager.getViewportWidth();

        final Bitmap dst = Bitmap.createBitmap(viewportWidth, viewportHeight, config);

        Canvas canvas = new Canvas(dst);
        final int left = (getRight() - viewportWidth) / 2;
        final int top = (getBottom() - viewportHeight) / 2;
        canvas.translate(-left, -top);

        drawBitmap(canvas);

        return dst;
    }

    /**
     * Obtain current viewport width.
     *
     * @return Current viewport width.
     * <p>Note: It might be 0 if layout pass has not been completed.</p>
     */
    public int getViewportWidth() {
        return touchManager.getViewportWidth();
    }

    /**
     * Obtain current viewport height.
     *
     * @return Current viewport height.
     * <p>Note: It might be 0 if layout pass has not been completed.</p>
     */
    public int getViewportHeight() {
        return touchManager.getViewportHeight();
    }

    /**
     * Offers common utility extensions.
     *
     * @return Extensions object used to perform chained calls.
     */
    public Extensions extensions() {
        if (extensions == null) {
            extensions = new Extensions(this);
        }
        return extensions;
    }

    /**
     * Optional extensions to perform common actions involving a {@link CropView}
     */
    public static class Extensions {

        private final CropView cropView;

        Extensions(CropView cropView) {
            this.cropView = cropView;
        }

        /**
         * Load a {@link Bitmap} using an automatically resolved {@link BitmapLoader} which will attempt to scale image to fill view.
         *
         * @param model Model used by {@link BitmapLoader} to load desired {@link Bitmap}
         * @see PicassoBitmapLoader
         * @see GlideBitmapLoader
         */
        public void load(@Nullable Object model) {
            new LoadRequest(cropView)
                    .load(model);
        }

        /**
         * Load a {@link Bitmap} using given {@link BitmapLoader}, you must call {@link LoadRequest#load(Object)} afterwards.
         *
         * @param bitmapLoader {@link BitmapLoader} used to load desired {@link Bitmap}
         * @see PicassoBitmapLoader
         * @see GlideBitmapLoader
         */
        public LoadRequest using(@Nullable BitmapLoader bitmapLoader) {
            return new LoadRequest(cropView).using(bitmapLoader);
        }

        /**
         * Perform an asynchronous crop request.
         *
         * @return {@link CropRequest} used to chain a configure cropping request, you must call either one of:
         * <ul>
         * <li>{@link CropRequest#into(File)}</li>
         * <li>{@link CropRequest#into(OutputStream, boolean)}</li>
         * </ul>
         */
        public CropRequest crop() {
            return new CropRequest(cropView);
        }

        /**
         * Perform a pick image request using {@link Activity#startActivityForResult(Intent, int)}.
         */
        public void pickUsing(@NonNull Activity activity, int requestCode) {
            CropViewExtensions.pickUsing(activity, requestCode);
        }
    }
}
