package com.pitchedapps.bubble.library.ui;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.view.animation.FastOutLinearInInterpolator;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.pitchedapps.bubble.library.utils.L;
import com.pitchedapps.bubble.library.utils.Utils;


/**
 * ViewGroup that holds the item UI elements. Allows configuring various parameters in relation
 * to UI like favicon, text indicator and is responsible for inflating all the content.
 */
public abstract class BaseUI extends FrameLayout {
    protected Context mContext;
    /**
     * Distance in pixels to be displaced when items are getting stacked
     */
    private static final int STACKING_GAP_PX = Utils.dpToPx(6);
    /**
     * Helper instance to know screen boundaries that item is allowed to travel
     */
    static ScreenBounds sScreenBounds;
    /**
     * Counter to keep count of active items
     */
    static int ITEM_COUNT = 0;
    /**
     * Static window manager instance to update, add and remove items
     */
    private static WindowManager sWindowManager;
    /**
     * Window parameters used to track and update items post creation;
     */
    WindowManager.LayoutParams mWindowParams;
    /**
     * Color of the item when removed
     */
    int mDeleteColor = 0xffff1744;

    int sDispWidth, sDispHeight;
    /**
     * Flag to know if the user moved manually or if the items is still resting
     */
    boolean mUserManuallyMoved;
    /**
     * If item was issued with destroy before.
     */
    boolean mDestroyed;
    /**
     * X icon drawable used when closing
     */
    private static Drawable sXDrawable;
    public ImageView mIcon;
    /**
     * The content view group which host all our elements
     */
    protected FrameLayout mContentGroup;

    public interface AnimInterface {
        void callback();
    }

    protected abstract @NonNull FrameLayout inflateContent(Context context);
    protected abstract @NonNull ImageView setImageView(FrameLayout frameLayout);
    protected abstract void setViewElevations(int px);

    protected FrameLayout getFrameLayoutFromID(@LayoutRes int id) {
        return (FrameLayout) LayoutInflater.from(getContext()).inflate(id, this, false);
    }

    protected ImageView getImageViewFromID(@IdRes int id) {
        if (mContentGroup == null) return null;
        return (ImageView) findViewById(id);
    }

    BaseUI(@NonNull Context context)  {
        super(context);
        mContext = context;

        sWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        mContentGroup = inflateContent(context);
        addView(mContentGroup);
        mIcon = setImageView(mContentGroup);

        mWindowParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        mWindowParams.gravity = Gravity.TOP | Gravity.START;

        initDisplayMetrics();
        setSpawnLocation();

        ITEM_COUNT++;

        sWindowManager.addView(this, mWindowParams);

        sXDrawable = new IconicsDrawable(context)
                .icon(GoogleMaterial.Icon.gmd_clear)
                .color(Color.WHITE)
                .sizeDp(18);
    }

    protected int getColorFromRes(@ColorRes int i) {
        return ContextCompat.getColor(mContext, i);
    }

    protected void setDeleteColor(@ColorInt int i) {
        mDeleteColor = i;
    }

    private void initDisplayMetrics() {
        final DisplayMetrics metrics = new DisplayMetrics();
        sWindowManager.getDefaultDisplay().getMetrics(metrics);
        sDispWidth = metrics.widthPixels;
        sDispHeight = metrics.heightPixels;

        mWindowParams.y = sDispHeight / 3;
    }

    /**
     * Listens for layout events and once width is measured, sets the initial spawn location based on
     * user preference
     */
    private void setSpawnLocation() {
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
//            @SuppressLint("RtlHardcoded")
            @Override
            public void onGlobalLayout() {
                if (sScreenBounds == null)
                    sScreenBounds = new ScreenBounds(sDispWidth, sDispHeight, getWidth());

                getViewTreeObserver().removeOnGlobalLayoutListener(this);

                mWindowParams.x = sScreenBounds.right;
                updateView();
            }
        });
    }

    /**
     * Used to get an instance of remove view
     *
     * @return an instance of {@link RemoveItem}
     */
    RemoveItem getRemoveView() {
        return RemoveItem.get(getContext());
    }

    /**
     * Wrapper around window manager to update this view. Called to move the item usually.
     */
    void updateView() {
        try {
            sWindowManager.updateViewLayout(this, mWindowParams);
        } catch (IllegalArgumentException e) {
            L.e("Update called after view was removed");
        }
    }

    /**
     * @return true if current item is the last active one
     */
    boolean isLastItem() {
        return ITEM_COUNT == 0;
    }

    //Should be written from lowest to highest
    private void setViewElevationsHelper(int baseElevation, View... vv) {
        for (View v : vv) {
            if (v == null) {
                L.e("setWebHeadElevation view is null");
                return;
            }
        }
        for (View v : vv) {
            v.setElevation(baseElevation);
            baseElevation++;
        }
    }

    @Nullable
    public ValueAnimator getStackDistanceAnimator() {
        ValueAnimator animator = null;
        if (!mUserManuallyMoved) {
            animator = ValueAnimator.ofInt(mWindowParams.y, mWindowParams.y + STACKING_GAP_PX);
            animator.setInterpolator(new FastOutLinearInInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mWindowParams.y = (int) animation.getAnimatedValue();
                    updateView();
                }
            });
            if (ITEM_COUNT > 2) {
                setViewElevations(Utils.dpToPx(4));
            }
        }
        return animator;
    }

    /**
     * Reveal goes from max scale to 0 appearing to be
     * revealing in.
     *
     * @param newIconColor The color to apply to circle background after animation is done
     * @return animator
     */
    protected abstract Animator getRevealInAnimator(@ColorInt final int newIconColor);

    /**
     * Applies a cross fade animation to transform the current favicon to an X icon.
     */
    void crossFadeFaviconToX() {
        mIcon.setVisibility(VISIBLE);
        mIcon.clearAnimation();
        mIcon.setScaleType(ImageView.ScaleType.CENTER);
        final TransitionDrawable icon = new TransitionDrawable(
                new Drawable[]{
                        new ColorDrawable(Color.TRANSPARENT),
                        sXDrawable
                });
        mIcon.setImageDrawable(icon);
        icon.setCrossFadeEnabled(true);
        icon.startTransition(50);
        mIcon
                .animate()
                .withLayer()
                .rotation(180)
                .setDuration(250)
                .setInterpolator(new LinearOutSlowInInterpolator())
                .start();
    }

    @Nullable
    public Bitmap getFaviconBitmap() {
        try {
            RoundedBitmapDrawable roundedBitmapDrawable = (RoundedBitmapDrawable) getFaviconDrawable();
            return roundedBitmapDrawable != null ? roundedBitmapDrawable.getBitmap() : null;
        } catch (Exception e) {
            L.e("Error while getting favicon bitmap: %s", e.getMessage());
        }
        return null;
    }

    @Nullable
    private Drawable getFaviconDrawable() {
        try {
            TransitionDrawable drawable = (TransitionDrawable) mIcon.getDrawable();
            if (drawable != null) {
                return drawable.getDrawable(1);
            } else
                return null;
        } catch (ClassCastException e) {
            L.e("Error while getting favicon drawable: %s", e.getMessage());
        }
        return null;
    }

    public void setFaviconDrawable(@NonNull final Drawable faviconDrawable) {
        if (mIcon != null) {
            TransitionDrawable transitionDrawable = new TransitionDrawable(
                    new Drawable[]{
                            new ColorDrawable(Color.TRANSPARENT),
                            faviconDrawable
                    });
            mIcon.setVisibility(VISIBLE);
            mIcon.setImageDrawable(transitionDrawable);
            transitionDrawable.setCrossFadeEnabled(true);
            transitionDrawable.startTransition(500);
        }
    }

    @SuppressWarnings("UnusedParameters")
    void destroySelf(final boolean receiveCallback) {
        mDestroyed = true;
        RemoveItem.disappear();
        removeView(mContentGroup);
        if (sWindowManager != null)
            sWindowManager.removeView(this);
    }

    /**
     * Helper class to hold screen boundaries
     */
    class ScreenBounds {
        /**
         * Amount of item that will be displaced off of the screen horizontally
         */
        private static final double DISPLACE_PERC = 0.7;

        public int left;
        public int right;
        public int top;
        public int bottom;

        public ScreenBounds(int dispWidth, int dispHeight, int webHeadWidth) {
            if (webHeadWidth == 0 || dispWidth == 0 || dispHeight == 0) {
                throw new IllegalArgumentException("Width of item or screen size cannot be 0");
            }
            right = (int) (dispWidth - (webHeadWidth * DISPLACE_PERC));
            left = (int) (webHeadWidth * (1 - DISPLACE_PERC)) * -1;
            top = Utils.dpToPx(25);
            bottom = (int) (dispHeight * 0.85);
        }
    }
}
