package com.pitchedapps.bubble.library.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.text.SpannableString;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.pitchedapps.bubble.library.B;
import com.pitchedapps.bubble.library.R;
import com.pitchedapps.bubble.library.item.BubbleStyle;
import com.pitchedapps.bubble.library.logging.BLog;
import com.pitchedapps.bubble.library.utils.ColourUtils;
import com.pitchedapps.bubble.library.utils.Constants;
import com.pitchedapps.bubble.library.utils.Utils;

import cn.nekocode.badge.BadgeDrawable;

import static com.pitchedapps.bubble.library.utils.Utils.dpToPx;

/**
 * ViewGroup that holds the bubble UI elements. Allows configuring various parameters in relation
 * to UI like favicon, text indicator and is responsible for inflating all the content.
 */
abstract class BubbleBase extends FrameLayout {
    // Helper instance to know screen boundaries that web head is allowed to travel
    static ScreenBounds sScreenBounds;
    // Counter to keep count of active web heads
    static int WEB_HEAD_COUNT = 0;
    // Class variables to keep track of where the master was last touched down
    static int masterDownX;
    static int masterDownY;
    // Static window manager instance to update, add and remove web heads
    private static WindowManager sWindowManager;
    // X icon drawable used when closing
    private static Drawable sXDrawable;
    // Badge indicator
    private static BadgeDrawable sBadgeDrawable;
    // Class variables to keep track of master movements
    private static int masterX;
    private static int masterY;
    // Window parameters used to track and update web heads post creation;
    final WindowManager.LayoutParams mWindowParams;
    // Color of web head when removed
    int sDeleteColor = Constants.NO_COLOR;
    // Unique bubble key
    private final String mKey;

    private B.Position bubblesSpawnLocation;

    protected ImageView mFavicon;
    protected TextView mIndicator;
    protected ElevatedCircleView mCircleBackground;
    protected CircleView mRevealView;
    protected TextView mBadgeView;

    // Display dimensions
    int sDispWidth, sDispHeight;
    // The content view group which host all our elements
    FrameLayout mContentGroup;
    // Flag to know if the user moved manually or if the web heads is still resting
    boolean mUserManuallyMoved;
    // If web head was issued with destroy before.
    boolean mDestroyed;
    // Master Wayne
    boolean mMaster;
    // If this web head is being queued to be displayed on screen.
    boolean mInQueue;

    private boolean mSpawnSet;
    // Color of the web head
    @ColorInt
    int mBubbleColor;

    @SuppressLint("RtlHardcoded")
    BubbleBase(@NonNull Context context, @NonNull String key) {
        super(context);
        WEB_HEAD_COUNT++;
        mKey = key;
        sWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        inflateContent(context);
        initContent();

        mWindowParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        mWindowParams.gravity = Gravity.TOP | Gravity.LEFT;

        initDisplayMetrics();

        sWindowManager.addView(this, mWindowParams);

        if (sXDrawable == null) {
            sXDrawable = new IconicsDrawable(context)
                    .icon(GoogleMaterial.Icon.gmd_close)
                    .color(Color.WHITE)
                    .sizeDp(18);
        }

        if (sDeleteColor == Constants.NO_COLOR)
            sDeleteColor = ContextCompat.getColor(context, R.color.remove_web_head_color);

        // Needed to prevent overly dark shadow.
        if (WEB_HEAD_COUNT > 2) {
            setBubbleElevation(dpToPx(4));
        }
    }

    public static void clearMasterPosition() {
        masterY = 0;
        masterX = 0;
    }

    protected abstract void onMasterChanged(boolean master);

    /**
     * Event for sub class to get notified once spawn location is set.
     *
     * @param x X
     * @param y Y
     */
    protected abstract void onSpawnLocationSet(int x, int y);

    private void inflateContent(@NonNull Context context) {
        // TODO size
        mContentGroup = (FrameLayout) LayoutInflater.from(getContext()).inflate(R.layout.web_head_layout, this, false);
        addView(mContentGroup);
        mFavicon = (ImageView) mContentGroup.findViewById(R.id.favicon);
        mIndicator = (TextView) mContentGroup.findViewById(R.id.indicator);
        mCircleBackground = (ElevatedCircleView) mContentGroup.findViewById(R.id.circleBackground);
        mRevealView = (CircleView) mContentGroup.findViewById(R.id.revealView);
        mBadgeView = (TextView) mContentGroup.findViewById(R.id.badge);
        postInflateContent(context, mContentGroup);
    }

    protected abstract void postInflateContent(@NonNull Context context, @NonNull FrameLayout mFrame);

    private void initDisplayMetrics() {
        final DisplayMetrics metrics = new DisplayMetrics();
        sWindowManager.getDefaultDisplay().getMetrics(metrics);
        sDispWidth = metrics.widthPixels;
        sDispHeight = metrics.heightPixels;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (sScreenBounds == null)
            sScreenBounds = new ScreenBounds(sDispWidth, sDispHeight, w);

        if (!mSpawnSet) {
            int x, y = sDispHeight / 3;

            if (masterX != 0 || masterY != 0) {
                x = masterX;
                y = masterY;
            } else {
                if (bubblesSpawnLocation == B.Position.RIGHT) {
                    x = sScreenBounds.right;
                } else {
                    x = sScreenBounds.left;
                }
            }
            mSpawnSet = true;
            onSpawnLocationSet(x, y);
        }
    }

    /**
     * A way for you to customize the bubble colours and text before loading
     * This cannot be null, but you can just pass the default new BubbleStyle() for the default colours
     * Default colour is a random dark colour
     * Default indicator is the first letter of the key
     * @param context view context
     * @param key bubble key
     * @return BubbleStyle
     */
    protected abstract
    @NonNull
    BubbleStyle styleBubble(Context context, String key);

    /**
     * Initializes web head from user preferences
     */
    private void initContent() {
        BubbleStyle bubbleStyle = styleBubble(getContext(), mKey);
        mBubbleColor = bubbleStyle.getBackgroundColor();
        String indicator = bubbleStyle.getIndicatorText();
        if (bubbleStyle.getIconDrawable() != null) {
            setFaviconDrawable(bubbleStyle.getIconDrawable());
            if (indicator != null) mIndicator.setText(indicator);
        } else {
            if (indicator == null) indicator = mKey.substring(0, 1);
            mIndicator.setText(indicator);
        }
        mIndicator.setTextColor(ColourUtils.getForegroundWhiteOrBlack(mBubbleColor));
        initRevealView(mBubbleColor);

        if (sBadgeDrawable == null) {
            sBadgeDrawable = new BadgeDrawable.Builder()
                    .type(BadgeDrawable.TYPE_NUMBER)
                    .badgeColor(ContextCompat.getColor(getContext(), R.color.colorAccent))
                    .textColor(Color.WHITE)
                    .number(WEB_HEAD_COUNT)
                    .build();
        } else {
            sBadgeDrawable.setNumber(WEB_HEAD_COUNT);
        }
        mBadgeView.setVisibility(VISIBLE);
        mBadgeView.setText(new SpannableString(sBadgeDrawable.toSpannable()));
        updateBadgeColors(mBubbleColor);

        pad(mBadgeView, 5);
    }

    protected boolean pad(View v, final int padDP) {
        if (Utils.isLollipopAbove()) return false;
        final int pad = dpToPx(padDP);
        v.setPadding(pad, pad, pad, pad);
        return true;
    }

    /**
     * Used to get an instance of remove web head
     *
     * @return an instance of {@link RemoveBubble}
     */
    RemoveBubble getRemoveBubble() {
        return RemoveBubble.get(getContext());
    }

    /**
     * Wrapper around window manager to update this view. Called to move the web head usually.
     */
    void updateView() {
        try {
            if (mMaster) {
                masterX = mWindowParams.x;
                masterY = mWindowParams.y;
            }
            sWindowManager.updateViewLayout(this, mWindowParams);
        } catch (IllegalArgumentException e) {
            BLog.e("Update called after view was removed");
        }
    }

    /**
     * @return true if current web head is the last active one
     */
    boolean isLastBubble() {
        return WEB_HEAD_COUNT == 0;
    }

    private void setBubbleElevation(int elevationPX) {
        if (Utils.isLollipopAbove()) {
            if (mCircleBackground != null && mRevealView != null) {
                mCircleBackground.setElevation(elevationPX);
                mRevealView.setElevation(elevationPX + 1);
            }
        }
    }

    @NonNull
    public Animator getRevealAnimator(@ColorInt final int newBubbleColor) {
        mRevealView.clearAnimation();
        initRevealView(newBubbleColor);

        AnimatorSet animator = new AnimatorSet();
        animator.playTogether(
                ObjectAnimator.ofFloat(mRevealView, "scaleX", 1f),
                ObjectAnimator.ofFloat(mRevealView, "scaleY", 1f),
                ObjectAnimator.ofFloat(mRevealView, "alpha", 1f)
        );
        mRevealView.setLayerType(LAYER_TYPE_HARDWARE, null);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mBubbleColor = newBubbleColor;
                updateBadgeColors(mBubbleColor);
                if (mIndicator != null && mCircleBackground != null && mRevealView != null) {
                    mCircleBackground.setColor(newBubbleColor);
//                    mIndicator.setTextColor(ColorUtil.getForegroundWhiteOrBlack(newBubbleColor)); //TODO
                    mRevealView.setLayerType(LAYER_TYPE_NONE, null);
                    mRevealView.setScaleX(0f);
                    mRevealView.setScaleY(0f);
                }
            }
        });
        animator.setInterpolator(new LinearOutSlowInInterpolator());
        animator.setDuration(250);
        return animator;
    }

    /**
     * Opposite of {@link #getRevealAnimator(int)}. Reveal goes from max scale to 0 appearing to be
     * revealing in.
     *
     * @param newBubbleColor New color of reveal
     * @param start          Runnable to run on start
     * @param end            Runnable to run on end
     */
    void revealInAnimation(@ColorInt final int newBubbleColor, @NonNull final Runnable start, @NonNull final Runnable end) {
        if (mRevealView == null || mCircleBackground == null) {
            start.run();
            end.run();
        }
        mRevealView.clearAnimation();
        mRevealView.setColor(mCircleBackground.getColor());
        mRevealView.setScaleX(1f);
        mRevealView.setScaleY(1f);
        mRevealView.setAlpha(1f);
        mCircleBackground.setColor(newBubbleColor);
        final AnimatorSet animator = new AnimatorSet();
        animator.playTogether(
                ObjectAnimator.ofFloat(mRevealView, "scaleX", 0f),
                ObjectAnimator.ofFloat(mRevealView, "scaleY", 0f)
        );
        mRevealView.setLayerType(LAYER_TYPE_HARDWARE, null);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                start.run();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mBubbleColor = newBubbleColor;
//                mIndicator.setTextColor(ColorUtil.getForegroundWhiteOrBlack(newBubbleColor));
                mRevealView.setLayerType(LAYER_TYPE_NONE, null);
                mRevealView.setScaleX(0f);
                mRevealView.setScaleY(0f);
                end.run();
            }

        });
        animator.setInterpolator(new LinearOutSlowInInterpolator());
        animator.setDuration(400);
        animator.setStartDelay(100);
        animator.start();
    }

    /**
     * Resets the reveal color so that it is ready to being reveal animation
     *
     * @param revealColor The color to appear during animation
     */
    private void initRevealView(@ColorInt int revealColor) {
        mRevealView.setColor(revealColor);
        mRevealView.setScaleX(0f);
        mRevealView.setScaleY(0f);
        mRevealView.setAlpha(0.8f);
    }

    /**
     * Applies a cross fade animation to transform the current favicon to an X icon. Ensures favicon
     * is visible by hiding indicators.
     */
    void crossFadeFaviconToX() {
        mFavicon.setVisibility(VISIBLE);
        mFavicon.clearAnimation();
        mFavicon.setScaleType(ImageView.ScaleType.CENTER);
        final TransitionDrawable icon = new TransitionDrawable(
                new Drawable[]{
                        new ColorDrawable(Color.TRANSPARENT),
                        sXDrawable
                });
        mFavicon.setImageDrawable(icon);
        icon.setCrossFadeEnabled(true);
        icon.startTransition(50);
        mFavicon
                .animate()
                .withLayer()
                .rotation(180)
                .setDuration(250)
                .setInterpolator(new LinearOutSlowInInterpolator())
                .start();
    }

    @SuppressWarnings("SameParameterValue")
    @ColorInt
    public int getBubbleColor(boolean ignoreFavicons) {
        if (ignoreFavicons) {
            return mBubbleColor;
        } else {
            if (getFaviconBitmap() != null) {
                return mBubbleColor;
            } else return Constants.NO_COLOR;
        }
    }

    public void setBubbleColor(@ColorInt int bubbleColor) {
        getRevealAnimator(bubbleColor).start();
    }

    void updateBadgeColors(@ColorInt int bubbleColor) {
//        final int badgeColor = ColorUtil.getClosestAccentColor(bubbleColor);
//        sBadgeDrawable.setBadgeColor(badgeColor);
//        sBadgeDrawable.setTextColor(ColorUtil.getForegroundWhiteOrBlack(badgeColor));
//        mBadgeView.invalidate();
    }

    @NonNull
    public ImageView getFaviconView() {
        return mFavicon;
    }

    @NonNull
    public String getKey() {
        return mKey;
    }

    @Nullable
    public Bitmap getFaviconBitmap() {
        try {
            final RoundedBitmapDrawable roundedBitmapDrawable = (RoundedBitmapDrawable) getFaviconDrawable();
            return roundedBitmapDrawable != null ? roundedBitmapDrawable.getBitmap() : null;
        } catch (Exception e) {
            BLog.e("Error while getting favicon bitmap: %s", e.getMessage());
        }
        return null;
    }

    @Nullable
    private Drawable getFaviconDrawable() {
        try {
            TransitionDrawable drawable = (TransitionDrawable) mFavicon.getDrawable();
            if (drawable != null) {
                return drawable.getDrawable(1);
            } else
                return null;
        } catch (ClassCastException e) {
            BLog.e("Error while getting favicon drawable: %s", e.getMessage());
        }
        return null;
    }

    public void setFaviconDrawable(@NonNull final Drawable faviconDrawable) {
        if (mIndicator != null && mFavicon != null) {
            mIndicator.animate().alpha(0).withLayer().start();
            TransitionDrawable transitionDrawable = new TransitionDrawable(
                    new Drawable[]{
                            new ColorDrawable(Color.TRANSPARENT),
                            faviconDrawable
                    });
            mFavicon.setVisibility(VISIBLE);
            mFavicon.setImageDrawable(transitionDrawable);
            transitionDrawable.setCrossFadeEnabled(true);
            transitionDrawable.startTransition(500);
        }
    }

    @SuppressWarnings("UnusedParameters")
    void destroySelf(boolean receiveCallback) {
        mDestroyed = true;
        RemoveBubble.disappear();
        removeView(mContentGroup);
        if (sWindowManager != null)
            try {
                sWindowManager.removeView(this);
            } catch (Exception ignored) {
            }
    }

    public boolean isMaster() {
        return mMaster;
    }

    public void setMaster(boolean master) {
        this.mMaster = master;
        if (!master) {
            mBadgeView.setVisibility(INVISIBLE);
        } else {
            mBadgeView.setVisibility(VISIBLE);
            sBadgeDrawable.setNumber(WEB_HEAD_COUNT);
            setInQueue(false);
        }
        onMasterChanged(master);
    }

    public void setInQueue(boolean inQueue) {
        this.mInQueue = inQueue;
        if (inQueue) {
            setVisibility(GONE);
        } else {
            setVisibility(VISIBLE);
        }
    }

    /**
     * Helper class to hold screen boundaries
     */
    class ScreenBounds {
        /**
         * Amount of web head that will be displaced off of the screen horizontally
         */
        private static final double DISPLACE_PERC = 0.7;

        public int left;
        public int right;
        public int top;
        public int bottom;

        ScreenBounds(int dispWidth, int dispHeight, int bubbleWidth) {
            if (bubbleWidth == 0 || dispWidth == 0 || dispHeight == 0) {
                throw new IllegalArgumentException("Width of web head or screen size cannot be 0");
            }
            right = (int) (dispWidth - (bubbleWidth * DISPLACE_PERC));
            left = (int) (bubbleWidth * (1 - DISPLACE_PERC)) * -1;
            top = dpToPx(25);
            bottom = (int) (dispHeight * 0.85);
        }
    }
}
