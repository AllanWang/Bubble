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
import com.pitchedapps.bubble.library.utils.L;
import com.pitchedapps.bubble.library.utils.Utils;
import com.pitchedapps.bubble.library.physics.MovementTracker;

/**
 * Web head object which adds draggable and gesture functionality.
 */
@SuppressLint("ViewConstructor")
public abstract class BubbleUI extends BaseUI implements SpringListener {

    public String key;
    private static final float TOUCH_DOWN_SCALE = 0.85f;
    private static final float TOUCH_UP_SCALE = 1f;
    /**
     * Coordinate of remove web head that we can lock on to.
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
     * Minimum horizontal velocity that we need to move the web head from one end of the screen
     * to another
     */
    private static int MINIMUM_HORIZONTAL_FLING_VELOCITY = 0;
    /**
     * Touch slop of the device
     */
    private final int mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    /**
     * Gesture detector to recognize fling and click on web heads
     */
    private final GestureDetector mGestureDetector = new GestureDetector(getContext(), new GestureDetectorListener());
    /**
     * The base spring system to create our springs.
     */
    private SpringSystem mSpringSystem;
    /**
     * Individual springs to control X, Y and scale of the web head
     */
    private Spring mXSpring, mYSpring, mScaleSpring;

    private static final SpringConfig FLING_CONFIG = SpringConfig.fromOrigamiTensionAndFriction(50, 5);
    private static final SpringConfig DRAG_CONFIG = SpringConfig.fromOrigamiTensionAndFriction(0, 1.8);
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
    private ItemInteractionListener mInteractionListener = new ItemInteractionListener() {
        @Override
        public void onItemClick() {
            // noop
        }

        @Override
        public void onItemDestroyed(boolean isLastItem) {
            // noop
        }
    };

    /**
     * Inits the web head and attaches to the system window. It is assumed that draw over other apps permission is
     * granted for 6.0+.
     *
     * @param context  Service
     * @param key      Key for item for the linkedhashmap
     * @param listener for listening to events on the webhead
     */
    public BubbleUI(@NonNull Context context, @NonNull String key, @Nullable ItemInteractionListener listener) {
        super(context);
        if (listener != null) {
            mInteractionListener = listener;
        }
        this.key = key;
        mMovementTracker = MovementTracker.obtain();

        calcVelocities();

        setupSprings();
    }

    protected void calcVelocities() {
        if (MINIMUM_HORIZONTAL_FLING_VELOCITY == 0) {
            int scaledScreenWidthDp = (getResources().getConfiguration().screenWidthDp * 7);
            MINIMUM_HORIZONTAL_FLING_VELOCITY = Utils.dpToPx(scaledScreenWidthDp);
        }
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
            String msg = "NPE on web heads " + e.getMessage();
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
     * Responsible for moving the web heads around and for locking/unlocking the web head to
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
        RemoveItem.disappear();
        return false;
    }

    /**
     * Returns the coordinate where the web head should lock to the remove web heads.
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
     * Used to determine if the web head is in vicinity of remove web head view.
     *
     * @param x Current x position of web head
     * @param y Current y position of web head
     * @return true if near, false other wise
     */
    private boolean isNearRemoveCircle(int x, int y) {
        int[] p = getRemoveView().getCenterCoordinates();
        int rX = p[0];
        int rY = p[1];

        int offset = getWidth() / 2;
        x += offset;
        y += offset;

        if (dist(rX, rY, x, y) < RemoveItem.MAGNETISM_THRESHOLD) {
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
        mWindowParams.x = (int) mXSpring.getCurrentValue();
        mWindowParams.y = (int) mYSpring.getCurrentValue();
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
     * Makes the web head stick to either side of the wall.
     */
    private void stickToWall() {
        mXSpring.setSpringConfig(FLING_CONFIG);
        mXSpring.setEndValue((mWindowParams.x > sDispWidth / 2) ? sScreenBounds.right : sScreenBounds.left);
        L.e("Restored X");

        if (mWindowParams.y < sScreenBounds.top) {
            mYSpring.setSpringConfig(FLING_CONFIG);
            mYSpring.setEndValue(sScreenBounds.top);
            L.e("Restored Y");
        } else if (mWindowParams.y > sScreenBounds.bottom) {
            mYSpring.setSpringConfig(FLING_CONFIG);
            mYSpring.setEndValue(sScreenBounds.bottom);
            L.e("Restored Y");
        }
    }

    @Override
    public void destroySelf(final boolean receiveCallback) {
        mDestroyed = true;
        ITEM_COUNT--;
        destroySprings();
        if (isCurrentlyAtRemove()) {
            closeWithAnimation(receiveCallback);
        } else {
            if (receiveCallback) mInteractionListener.onItemDestroyed(isLastItem());
            super.destroySelf(receiveCallback);
        }
    }

    /**
     * Animates and closes the web head.
     */
    private void closeWithAnimation(final boolean receiveCallback) {
        final Animator reveal = getRevealInAnimator(mDeleteColor);
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
                                            mInteractionListener.onItemDestroyed(isLastItem());
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
     * Helper to know if the web head is currently locked in place with remove web head.
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

    public interface ItemInteractionListener {
        void onItemClick();

        void onItemDestroyed(boolean isLastItem);
    }

    /**
     * A gesture listener class to monitor standard fling and click events on the web head view.
     */
    private class GestureDetectorListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapConfirmed(MotionEvent event) {
            mWasClicked = true;
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
                    L.e("Error : %s", e.getMessage());
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
            return true;
        }

        private void sendCallback() {
            RemoveItem.disappear();
            mInteractionListener.onItemClick();
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            mDragging = false;

            float[] adjustedVelocities = mMovementTracker.getAdjustedVelocities(velocityX, velocityY);

            if (adjustedVelocities == null) {
                float[] down = new float[]{e1.getRawX(), e1.getRawY()};
                float[] up = new float[]{e2.getRawX(), e2.getRawY()};
                adjustedVelocities = MovementTracker.adjustVelocities(down, up, velocityX, velocityY);
            }

            if (adjustedVelocities != null) {
                mWasFlung = true;

                velocityX = interpolateXVelocity(e2, adjustedVelocities[0]);

                mXSpring.setSpringConfig(DRAG_CONFIG);
                mYSpring.setSpringConfig(DRAG_CONFIG);

                mXSpring.setVelocity(velocityX);
                mYSpring.setVelocity(adjustedVelocities[1]);
                return true;
            }
            return false;
        }

        /**
         * Attempts to figure out the correct X velocity by using {@link #MINIMUM_HORIZONTAL_FLING_VELOCITY}
         * This is needed since if we blindly upscale the velocity, web heads will jump too quickly
         * when near screen edges. This method proportionally upscales the velocity based on where the
         * web head was released to prevent quick  jumps.
         *
         * @param upEvent   Motion event of last touch release
         * @param velocityX original velocity
         * @return Scaled velocity
         */
        private float interpolateXVelocity(MotionEvent upEvent, float velocityX) {
            float x = upEvent.getRawX() / sDispWidth;
            if (velocityX > 0) {
                velocityX = Math.max(velocityX, MINIMUM_HORIZONTAL_FLING_VELOCITY * (1 - x));
            } else {
                velocityX = -Math.max(velocityX, MINIMUM_HORIZONTAL_FLING_VELOCITY * x);
            }
            return velocityX;
        }
    }

}
