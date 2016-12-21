package com.pitchedapps.bubble.library.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.BounceInterpolator;
import android.widget.FrameLayout;

import com.facebook.rebound.SimpleSpringListener;
import com.facebook.rebound.Spring;
import com.facebook.rebound.SpringConfig;
import com.facebook.rebound.SpringSystem;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;

import com.pitchedapps.bubble.library.R;
import com.pitchedapps.bubble.library.logging.BLog;
import com.pitchedapps.bubble.library.utils.Utils;

/**
 * Created by Arun on 03/02/2016.
 */
@SuppressLint("ViewConstructor")
public class RemoveBubble extends FrameLayout {

    static final double MAGNETISM_THRESHOLD = Utils.dpToPx(120);
    private static WindowManager sWindowManager;
    private static RemoveBubble sOurInstance;

    private WindowManager.LayoutParams mWindowParams;

    private int mDispWidth;
    private int mDispHeight;

    private Spring mScaleSpring;
    private SpringSystem mSpringSystem;

    private boolean mHidden;

    private RemoveBubbleCircle mRemoveBubbleCircle;

    private boolean mGrew;

    private int[] mCentrePoint = null;

    @SuppressLint("RtlHardcoded")
    private RemoveBubble(Context context, WindowManager windowManager) {
        super(context);
        sWindowManager = windowManager;

        mRemoveBubbleCircle = new RemoveBubbleCircle(context);
        addView(mRemoveBubbleCircle);

        mWindowParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);

        setDisplayMetrics();

        setVisibility(INVISIBLE);
        mHidden = true;

        mWindowParams.gravity = Gravity.LEFT | Gravity.TOP;
        int offset = getAdaptWidth() / 2;
        mWindowParams.x = (mDispWidth / 2) - offset;
        mWindowParams.y = mDispHeight - (mDispHeight / 6) - offset;

        setUpSprings();
        initCentreCoords();

        sWindowManager.addView(this, mWindowParams);
    }

    public static RemoveBubble get(Context context) {
        if (sOurInstance != null)
            return sOurInstance;
        else {
            BLog.d("Creating new instance of remove bubble");
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            sOurInstance = new RemoveBubble(context, windowManager);
            return sOurInstance;
        }
    }

    public static void destroy() {
        if (sOurInstance != null) {
            sOurInstance.destroySelf();
        }
    }

    public static void disappear() {
        if (sOurInstance != null) {
            sOurInstance.hide();
        }
    }

    public void destroyAnimator(final Runnable endAction) {
        if (sOurInstance == null || mRemoveBubbleCircle == null) endAction.run();

        sOurInstance.mRemoveBubbleCircle.animate()
                .scaleX(0.0f)
                .scaleY(0.0f)
                .alpha(0.5f)
                .setDuration(300)
                .withLayer()
                .withEndAction(endAction)
                .setInterpolator(new BounceInterpolator())
                .start();
    }

    private void destroySelf() {
        mScaleSpring.setAtRest();
        mScaleSpring.destroy();
        mScaleSpring = null;

        removeView(mRemoveBubbleCircle);
        mRemoveBubbleCircle = null;

        mWindowParams = null;

        mSpringSystem = null;

        sWindowManager.removeView(this);

        mCentrePoint = null;

        sOurInstance = null;
        BLog.d("Remove view detached and killed");
    }

    private int getAdaptWidth() {
        return Math.max(getWidth(), RemoveBubbleCircle.getSizePx());
    }

    int[] getCenterCoordinates() {
        if (mCentrePoint == null) {
            initCentreCoords();
        }
        return mCentrePoint;
    }

    private void initCentreCoords() {
        int offset = getAdaptWidth() / 2;
        int rX = getWindowParams().x + offset;
        int rY = getWindowParams().y + offset;
        mCentrePoint = new int[]{rX, rY};
    }

    private void setUpSprings() {
        mSpringSystem = SpringSystem.create();
        mScaleSpring = mSpringSystem.createSpring();

        SpringConfig scaleSpringConfig = SpringConfig.fromOrigamiTensionAndFriction(100, 9);
        mScaleSpring.setSpringConfig(scaleSpringConfig);
        mScaleSpring.addListener(new SimpleSpringListener() {
            @Override
            public void onSpringUpdate(Spring spring) {
                float value = (float) spring.getCurrentValue();
                mRemoveBubbleCircle.setScaleX(value);
                mRemoveBubbleCircle.setScaleY(value);
            }
        });
    }

    private void setDisplayMetrics() {
        final DisplayMetrics metrics = new DisplayMetrics();
        sWindowManager.getDefaultDisplay().getMetrics(metrics);
        mDispWidth = metrics.widthPixels;
        mDispHeight = metrics.heightPixels;
    }

    private WindowManager.LayoutParams getWindowParams() {
        return mWindowParams;
    }

    private void hide() {
        if (!mHidden) {
            mScaleSpring.setEndValue(0.0f);
            mHidden = true;
        }
    }

    void reveal() {
        setVisibility(VISIBLE);
        if (mHidden) {
            mScaleSpring.setEndValue(0.9f);
            mHidden = false;
        }
    }

    void grow() {
        if (!mGrew) {
            mScaleSpring.setCurrentValue(0.9f, true);
            mScaleSpring.setEndValue(1f);
            mGrew = true;
        }
    }

    void shrink() {
        if (mGrew) {
            mScaleSpring.setEndValue(0.9f);
            mGrew = false;
        }
    }

    /**
     * Created by Arun on 04/02/2016.
     */
    private static class RemoveBubbleCircle extends View {

        private static int sSizePx;
        private static int sDiameterPx;
        private final Paint mBgPaint;

        public RemoveBubbleCircle(Context context) {
            super(context);
            mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mBgPaint.setColor(ContextCompat.getColor(getContext(), R.color.remove_web_head_color));
            mBgPaint.setStyle(Paint.Style.FILL);

            float shadwR = context.getResources().getDimension(R.dimen.remove_head_shadow_radius);
            float shadwDx = context.getResources().getDimension(R.dimen.remove_head_shadow_dx);
            float shadwDy = context.getResources().getDimension(R.dimen.remove_head_shadow_dy);

            mBgPaint.setShadowLayer(shadwR, shadwDx, shadwDy, 0x75000000);

            setLayerType(View.LAYER_TYPE_SOFTWARE, null);

            sSizePx = context.getResources().getDimensionPixelSize(R.dimen.remove_head_size);
        }

        static int getSizePx() {
            return sSizePx;
        }

        public static int getDiameterPx() {
            return sDiameterPx;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            setMeasuredDimension(sSizePx, sSizePx);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            float radius = (float) (getWidth() / 2.4);
            canvas.drawCircle(getWidth() / 2, getHeight() / 2, radius, mBgPaint);

            drawDeleteIcon(canvas);

            sDiameterPx = (int) (2 * radius);
        }

        private void drawDeleteIcon(Canvas canvas) {
            Bitmap deleteIcon = new IconicsDrawable(getContext())
                    .icon(GoogleMaterial.Icon.gmd_delete)
                    .color(Color.WHITE)
                    .sizeDp(18).toBitmap();
            int cHeight = canvas.getClipBounds().height();
            int cWidth = canvas.getClipBounds().width();
            float x = cWidth / 2f - deleteIcon.getWidth() / 2;
            float y = cHeight / 2f - deleteIcon.getHeight() / 2;
            canvas.drawBitmap(deleteIcon, x, y, null);
        }
    }
}
