package com.pitchedapps.bubble.library.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.facebook.rebound.SimpleSpringListener;
import com.facebook.rebound.Spring;
import com.facebook.rebound.SpringConfig;
import com.facebook.rebound.SpringListener;
import com.facebook.rebound.SpringSystem;
import com.pitchedapps.bubble.library.physics.MovementTracker;
import com.pitchedapps.bubble.library.utils.L;
import com.pitchedapps.bubble.library.utils.Utils;

/**
 * bubble object which adds draggable and gesture functionality.
 */
@SuppressLint("ViewConstructor")
public abstract class BubbleUI extends BaseUI implements SpringListener {

    private static final float TOUCH_DOWN_SCALE = 0.85f;
    private static final float TOUCH_UP_SCALE = 1f;
    private int lastUnlinkedX, lastUnlinkedY;

    /**
     * Coordinate of remove bubble that we can lock on to.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private static int[] sTrashLockCoordinate;
    /**
     * True when being dragged, otherwise false
     */
    private boolean mDragging;
    /**
     * True when attached to remove view, otherwise false
     */
    private boolean mWasRemoveLocked;
    /**
     * True when fling detected and false on new touch event
     */
    private boolean mWasFlung;
    /**
     * True when click was detected, and false on new touch event
     */
    private boolean mWasClicked;
    /**
     * True when touched down and false otherwise
     */
    private boolean mScaledDown;
    /**
     * Minimum horizontal velocity that we need to move the bubble from one end of the screen
     * to another
     */
    private static int MINIMUM_HORIZONTAL_FLING_VELOCITY = 0;
    /**
     * Minimum velocity needed to fling the bubble to the other side
     */
    private static int HORIZONTAL_FLING_THRESHOLD = 0;
    /**
     * Touch slop of the device
     */
    private final int mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    /**
     * Gesture detector to recognize fling and click on bubbles
     */
    private final GestureDetector mGestureDetector = new GestureDetector(getContext(), new GestureDetectorListener());
    /**
     * The base spring system to create our springs.
     */
    private SpringSystem mSpringSystem;
    /**
     * Individual springs to control X, Y and scale of the bubble
     */
    private Spring mXSpring, mYSpring, mScaleSpring;

    private static final SpringConfig FLING_CONFIG = SpringConfig.fromOrigamiTensionAndFriction(50, 5);
    private static final SpringConfig FLY_CONFIG = SpringConfig.fromOrigamiTensionAndFriction(20, 5);
    private static final SpringConfig DRAG_CONFIG = SpringConfig.fromOrigamiTensionAndFriction(5, 1.8);
    private static final SpringConfig NO_TENSION_CONFIG = SpringConfig.fromOrigamiTensionAndFriction(0, 1.8); //was 0, 1.8
    private static final SpringConfig SNAP_CONFIG = SpringConfig.fromOrigamiTensionAndFriction(100, 7);
    /**
     * Movement tracker instance, that is used to adjust X and Y velocity calculated by {@link #mGestureDetector}.
     * This is needed since sometimes velocities coming from {@link GestureDetectorListener#onFling(MotionEvent, MotionEvent, float, float)}
     * has wrong polarity.
     */
    private final MovementTracker mMovementTracker;

    private float posX, posY;
    private int initialDownX, initialDownY;

    /**
     * The interaction listener that clients can provide to listen for events on webhead.
     */
    private BubbleInteractionListener mInteractionListener = new BubbleInteractionListener() {
        @Override
        public void onBubbleClick(BubbleUI bubbleUI) {

        }

        @Override
        public void onBubbleDestroyed(BubbleUI bubbleUI, boolean isLastBubble) {

        }
    };

    private BubbleUIServiceListener mServiceListener = new BubbleUIServiceListener() {
        @Override
        public void onBubbleSpringPositionUpdate(int x, int y) {

        }
    };

    /**
     * Inits the bubble and attaches to the system window. It is assumed that draw over other apps permission is
     * granted for 6.0+.
     *
     * @param context Service
     * @param key     Key for bubble for the linkedhashmap
     */
    public BubbleUI(@NonNull Context context, @NonNull String key) {
        super(context);

        this.key = key;
        mMovementTracker = MovementTracker.obtain();

        calcVelocities();

        setupSprings();
    }

    public void addInteractionListener(@Nullable BubbleInteractionListener listener) {
        if (listener != null) {
            mInteractionListener = listener;
        }
    }

    public void addServiceListener(BubbleUIServiceListener serviceListener) {
        mServiceListener = serviceListener;
    }

    protected void calcVelocities() {
        if (MINIMUM_HORIZONTAL_FLING_VELOCITY == 0 || HORIZONTAL_FLING_THRESHOLD == 0) {
            int scaledWidthDp = getResources().getConfiguration().screenWidthDp;
            MINIMUM_HORIZONTAL_FLING_VELOCITY = Utils.dpToPx(scaledWidthDp * 7);
            HORIZONTAL_FLING_THRESHOLD = Utils.dpToPx(scaledWidthDp / 5);
        }
    }

    public void linkBubble(boolean b, int x, int y) {
        //from nonlink to link
        if (!linked && b && x != -1 && y != -1) {
            y += position * BaseUI.STACKING_GAP_PX;

            lastUnlinkedX = mWindowParams.x;
            lastUnlinkedY = mWindowParams.y;

            mXSpring.setSpringConfig(FLY_CONFIG);
            mXSpring.setEndValue(x);

            mYSpring.setSpringConfig(FLY_CONFIG);
            mYSpring.setEndValue(y);
            //from link to nonlink
        } else if (linked && !b) {
            mXSpring.setSpringConfig(FLY_CONFIG);
            mXSpring.setEndValue(lastUnlinkedX);

            mYSpring.setSpringConfig(FLY_CONFIG);
            mYSpring.setEndValue(lastUnlinkedY);
        }
        linked = b;
    }

    public void linkBubbleStart(boolean b, int x, int y) {
        linked = b;
        if (linked && x != -1 && y != -1) {
            mWindowParams.x = x;
            mWindowParams.y = y;
            lastUnlinkedX = mWindowParams.x;
            lastUnlinkedY = mWindowParams.y;
        }
    }

    public boolean isLinked() {
        return linked;
    }

    public void setPosition(int i) {
        position = i;
    }

    public int getPosition() {
        return position;
    }

    private void setupSprings() {
        mSpringSystem = SpringSystem.create();
        mYSpring = mSpringSystem.createSpring();
        mYSpring.addListener(this);
        mXSpring = mSpringSystem.createSpring();
        mXSpring.addListener(this);
        mScaleSpring = mSpringSystem.createSpring();
        mScaleSpring.addListener(new SimpleSpringListener() {
            @Override
            public void onSpringUpdate(Spring spring) {
                float howMuch = (float) spring.getCurrentValue();
                mContentGroup.setScaleX(howMuch);
                mContentGroup.setScaleY(howMuch);
            }
        });
        mScaleSpring.setCurrentValue(0.0f, true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Don't react to any touch event and consume it when we are being destroyed
        if (mDestroyed) return true;
        if (linked && position != 0) return true;
        try {
            // Reset gesture flag on each event
            mWasFlung = false;
            mWasClicked = false;

            // Let gesture detector intercept events, needed for fling and click
            mGestureDetector.onTouchEvent(event);

            if (mWasClicked) return true;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    handleTouchDown(event);
                    break;
                case MotionEvent.ACTION_MOVE:
                    handleMove(event);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (handleTouchUp())
                        return true;
                    break;
                default:
                    break;
            }
        } catch (NullPointerException e) {
            String msg = "NPE on bubbles " + e.getMessage();
            L.e(msg);
            destroySelf(true);
        }
        return true;
    }

    private void handleTouchDown(@NonNull MotionEvent event) {
        mDragging = false;

        mMovementTracker.onDown();

        initialDownX = mWindowParams.x;
        initialDownY = mWindowParams.y;

        posX = event.getRawX();
        posY = event.getRawY();

        touchDown();
    }

    /**
     * Responsible for moving the bubbles around and for locking/unlocking the bubble to
     * remove view.
     *
     * @param event the touch event
     */
    private void handleMove(@NonNull MotionEvent event) {
        mMovementTracker.addMovement(event);

        float offsetX = event.getRawX() - posX;
        float offsetY = event.getRawY() - posY;

        if (Math.hypot(offsetX, offsetY) > mTouchSlop) {
            mDragging = true;
        }

        if (mDragging) {
            getRemoveView().reveal();

            mUserManuallyMoved = true;

            int x = (int) (initialDownX + offsetX);
            int y = (int) (initialDownY + offsetY);

            if (isNearRemoveCircle(x, y)) {
                getRemoveView().grow();
                touchUp();

                mXSpring.setSpringConfig(SNAP_CONFIG);
                mYSpring.setSpringConfig(SNAP_CONFIG);

                mXSpring.setEndValue(trashLockCoOrd()[0]);
                mYSpring.setEndValue(trashLockCoOrd()[1]);

            } else {
                getRemoveView().shrink();

                mXSpring.setSpringConfig(DRAG_CONFIG);
                mYSpring.setSpringConfig(DRAG_CONFIG);

                mXSpring.setCurrentValue(x);
                mYSpring.setCurrentValue(y);

                touchDown();
            }
        }
    }

    private boolean handleTouchUp() {
        if (mWasRemoveLocked) {
            // If head was locked onto a remove bubble before, then kill ourselves
            destroySelf(true);
            return true;
        }
        mDragging = false;

        mMovementTracker.onUp();

        if (!mWasFlung && mUserManuallyMoved) {
            stickToWall();
        }
        touchUp();
        // hide remove view
        RemoveBubble.disappear();
        return false;
    }

    /**
     * Returns the coordinate where the bubble should lock to the remove bubbles.
     * Calculated once and reused there after.
     *
     * @return array of x and y.
     */
    private int[] trashLockCoOrd() {
        //if (sTrashLockCoordinate == null) {
        int[] removeCentre = getRemoveView().getCenterCoordinates();
        int offset = getWidth() / 2;
        int x = removeCentre[0] - offset;
        int y = removeCentre[1] - offset;
        sTrashLockCoordinate = new int[]{x, y};
        //}
        return sTrashLockCoordinate;
    }

    /**
     * Used to determine if the bubble is in vicinity of remove bubble view.
     *
     * @param x Current x position of bubble
     * @param y Current y position of bubble
     * @return true if near, false other wise
     */
    private boolean isNearRemoveCircle(int x, int y) {
        int[] p = getRemoveView().getCenterCoordinates();
        int rX = p[0];
        int rY = p[1];

        int offset = getWidth() / 2;
        x += offset;
        y += offset;

        if (dist(rX, rY, x, y) < RemoveBubble.MAGNETISM_THRESHOLD) {
            mWasRemoveLocked = true;
            return true;
        } else {
            mWasRemoveLocked = false;
            return false;
        }
    }

    private float dist(double x1, double y1, double x2, double y2) {
        return (float) Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    public void reveal() {
        mScaleSpring.setEndValue(TOUCH_UP_SCALE);
        mScaledDown = false;
    }

    private void touchDown() {
        if (!mScaledDown) {
            mScaleSpring.setEndValue(TOUCH_DOWN_SCALE);
            mScaledDown = true;
        }
    }

    private void touchUp() {
        if (mScaledDown) {
            mScaleSpring.setEndValue(TOUCH_UP_SCALE);
            mScaledDown = false;
        }
    }

    @Override
    public void onSpringUpdate(Spring spring) {
        int x = (int) mXSpring.getCurrentValue();
        int y = (int) mYSpring.getCurrentValue();
        if (position == 0 && linked) {
            mServiceListener.onBubbleSpringPositionUpdate(x, y);
        } else if (!linked) {
            updateBubblePosition(x, y);
        }
    }

    public void updateBubblePosition(int x, int y) {
        mWindowParams.x = x;
        y += position * BaseUI.STACKING_GAP_PX;
        mWindowParams.y = y;
        updateView();
        checkBounds();
    }

    @Override
    public void onSpringAtRest(Spring spring) {
    }

    @Override
    public void onSpringActivate(Spring spring) {
    }

    @Override
    public void onSpringEndStateChange(Spring spring) {

    }

    private void checkBounds() {
        // Only check when free
        if (mDragging) {
            return;
        }

        int x = mWindowParams.x;
        int y = mWindowParams.y;

        int width = getWidth();

        if (x + width >= sDispWidth) {
            mXSpring.setSpringConfig(FLING_CONFIG);
            mXSpring.setEndValue(sScreenBounds.right);
        }
        if (x - width <= 0) {
            mXSpring.setSpringConfig(FLING_CONFIG);
            mXSpring.setEndValue(sScreenBounds.left);
        }
        if (y + width >= sDispHeight) {
            mYSpring.setSpringConfig(FLING_CONFIG);
            mYSpring.setEndValue(sScreenBounds.bottom);
        }
        if (y - width <= 0) {
            mYSpring.setSpringConfig(FLING_CONFIG);
            mYSpring.setEndValue(sScreenBounds.top);
        }

        int minimumVelocityToReachSides = Utils.dpToPx(100);
        //noinspection StatementWithEmptyBody
        if (!mWasRemoveLocked
                && Math.abs(mXSpring.getVelocity()) < minimumVelocityToReachSides
                && Math.abs(mYSpring.getVelocity()) < minimumVelocityToReachSides
                && !mDragging) {
            // Commenting temporarily TODO investigate if causing any issue
            // stickToWall();
        }
    }

    /**
     * Makes the bubble stick to either side of the wall.
     */
    private void stickToWall() {
        mXSpring.setSpringConfig(FLING_CONFIG);
        mXSpring.setEndValue((mWindowParams.x > sDispWidth / 2) ? sScreenBounds.right : sScreenBounds.left);
        L.d("Restored X");

        if (mWindowParams.y < sScreenBounds.top) {
            mYSpring.setSpringConfig(FLING_CONFIG);
            mYSpring.setEndValue(sScreenBounds.top);
            L.d("Restored Y");
        } else if (mWindowParams.y > sScreenBounds.bottom) {
            mYSpring.setSpringConfig(FLING_CONFIG);
            mYSpring.setEndValue(sScreenBounds.bottom);
            L.d("Restored Y");
        }
    }

    @Override
    public void destroySelf(final boolean receiveCallback) {
        mDestroyed = true;
        BUBBLE_COUNT--;
        destroySprings();
        if (isCurrentlyAtRemove()) {
            closeWithAnimation(receiveCallback);
        } else {
            if (receiveCallback)
                mInteractionListener.onBubbleDestroyed(BubbleUI.this, isLastBubble());
            super.destroySelf(receiveCallback);
        }
    }

    /**
     * Animates and closes the bubble.
     */
    private void closeWithAnimation(final boolean receiveCallback) {
        final Animator reveal = getRevealInAnimator(mDeleteColor);
        if (reveal == null) {
            if (receiveCallback)
                mInteractionListener.onBubbleDestroyed(BubbleUI.this, isLastBubble());
            BubbleUI.super.destroySelf(receiveCallback);
            return;
        }
        mIcon
                .animate()
                .setDuration(200)
                .withLayer()
                .translationZ(0)
                .z(0)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        reveal.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationStart(Animator animation) {
                                crossFadeFaviconToX();
                            }

                            @Override
                            public void onAnimationEnd(Animator animation) {
                                new Handler().postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (receiveCallback)
                                            mInteractionListener.onBubbleDestroyed(BubbleUI.this, isLastBubble());
                                        BubbleUI.super.destroySelf(receiveCallback);
                                    }
                                }, 200);
                            }
                        });
                        reveal.setStartDelay(100);
                        reveal.start();
                    }
                });
    }

    /**
     * Helper to know if the bubble is currently locked in place with the remove view.
     *
     * @return true if locked, else false.
     */
    private boolean isCurrentlyAtRemove() {
        int rx = trashLockCoOrd()[0];
        int ry = trashLockCoOrd()[1];

        if (mWindowParams.x == rx && mWindowParams.y == ry) {
            return true;
        } else {
            double dist = dist(mWindowParams.x, mWindowParams.y, rx, ry);
            if (dist < Utils.dpToPx(15)) {
                L.d("Adjusting positions");
                mWindowParams.x = rx;
                mWindowParams.y = ry;
                updateView();
                return true;
            } else return false;
        }
    }

    private void destroySprings() {
        mSpringSystem.removeAllListeners();
        mXSpring.destroy();
        mYSpring.destroy();
    }

    public interface BubbleInteractionListener {
        void onBubbleClick(BubbleUI bubbleUI);

        void onBubbleDestroyed(BubbleUI bubbleUI, boolean isLastBubble);
    }

    public interface BubbleUIServiceListener {
        void onBubbleSpringPositionUpdate(int x, int y);
    }

    /**
     * A gesture listener class to monitor standard fling and click events on the bubble view.
     */
    private class GestureDetectorListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapConfirmed(MotionEvent event) {
            mWasClicked = true;
            sendCallback();
            return true;
        }

        private void sendCallback() {
            RemoveBubble.disappear();
            mInteractionListener.onBubbleClick(BubbleUI.this);
        }

        protected void removeBubble() {
            if (mContentGroup != null) {
                if (mWindowParams.x < sDispWidth / 2) {
                    mContentGroup.setPivotX(0);
                } else {
                    mContentGroup.setPivotX(mContentGroup.getWidth());
                }
                mContentGroup.setPivotY((float) (mContentGroup.getHeight() * 0.75));
                try {
                    mScaleSpring.setAtRest();
                } catch (Exception e) {
                    L.e("Error : ", e.getMessage());
                }
                mContentGroup.animate()
                        .scaleX(0.0f)
                        .scaleY(0.0f)
                        .alpha(0.5f)
                        .withLayer()
                        .setDuration(250)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                sendCallback();
                            }
                        })
                        .start();
            } else sendCallback();
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            mDragging = false;
            mWasFlung = true;
//            L.e("ORIG", velocityX, velocityY);

            float[] adjustedVelocities = mMovementTracker.getAdjustedVelocities(e1, e2, velocityX, velocityY);

            float velocityX2 = recalculateFlingVelocity(e2, adjustedVelocities[0]);
            float velocityY2 = adjustedVelocities[1];

            mXSpring.setSpringConfig(NO_TENSION_CONFIG);
            mYSpring.setSpringConfig(NO_TENSION_CONFIG);
////                L.e("NEW", velocityX2, velocityY2);
            mXSpring.setVelocity(velocityX2);
            mYSpring.setVelocity(velocityY2);
//                return true;
//            }
//L.e("NEWX", testAdjust(velocityX));
//            mXSpring.setVelocity((velocityX));
//            mXSpring.addListener(new AccelerationSpringListener(9.8));
//            mYSpring.setVelocity(velocityY);
            return true;
        }

        private float recalculateFlingVelocity(MotionEvent up, float velocityX) {
            float x = up.getRawX() / sDispWidth;
            if (velocityX > 0) {
                if (velocityX > HORIZONTAL_FLING_THRESHOLD) {
                    velocityX = Math.max(velocityX, MINIMUM_HORIZONTAL_FLING_VELOCITY * (1 - x));
                } else {
                    velocityX = -Math.max(velocityX, MINIMUM_HORIZONTAL_FLING_VELOCITY * x);
                }
            } else {
                if (-velocityX > HORIZONTAL_FLING_THRESHOLD) {
                    velocityX = -Math.max(velocityX, MINIMUM_HORIZONTAL_FLING_VELOCITY * x);
                } else {
                    velocityX = Math.max(velocityX, MINIMUM_HORIZONTAL_FLING_VELOCITY * (1 - x));
                }
            }
            return velocityX;
        }

        /**
         * Attempts to figure out the correct X velocity by using {@link #MINIMUM_HORIZONTAL_FLING_VELOCITY}
         * This is needed since if we blindly upscale the velocity, bubbles will jump too quickly
         * when near screen edges. This method proportionally upscales the velocity based on where the
         * bubble was released to prevent quick jumps.
         *
         * @param upEvent   Motion event of last touch release
         * @param velocityX original velocity
         * @return Scaled velocity
         */
        private float interpolateXVelocity(MotionEvent upEvent, float velocityX) {
            float x = upEvent.getRawX() / sDispWidth;
            if (velocityX > 0) {
                if (velocityX > HORIZONTAL_FLING_THRESHOLD) {
                    velocityX = Math.max(velocityX, MINIMUM_HORIZONTAL_FLING_VELOCITY * (1 - x));
                } else {
                    velocityX = -Math.max(velocityX, MINIMUM_HORIZONTAL_FLING_VELOCITY * x);
                }
            } else {
                if (-velocityX > HORIZONTAL_FLING_THRESHOLD) {
                    velocityX = -Math.max(velocityX, MINIMUM_HORIZONTAL_FLING_VELOCITY * x);
                } else {
                    velocityX = Math.max(velocityX, MINIMUM_HORIZONTAL_FLING_VELOCITY * (1 - x));
                }
            }
            return velocityX;
        }

        private float testAdjust(float velocity) {
            L.e("XX", velocity);
            return (Math.abs(velocity) < HORIZONTAL_FLING_THRESHOLD ? -velocity : velocity);
        }
    }

}
