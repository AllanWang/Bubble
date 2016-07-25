package com.pitchedapps.bubble.library;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.NonNull;

import com.pitchedapps.bubble.library.ui.BubbleUI;
import com.pitchedapps.bubble.library.ui.RemoveBubble;
import com.pitchedapps.bubble.library.utils.L;
import com.pitchedapps.bubble.library.utils.PositionUtils;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by Allan Wang on 2016-07-09.
 */
public class BubbleService extends Service implements BubbleUI.BubbleUIServiceListener, BubbleUI.BubbleInteractionListener {

    protected Context mContext;
    public PositionUtils mPositionUtils = new PositionUtils();
    private IBinder mBinder = new LocalBinder();
    private static BubbleService sInstance;
    private static boolean linkedBubbles = false;
    private int firstBubbleX, firstBubbleY;
    private BubbleActivityServiceListener mActivityListener = new BubbleActivityServiceListener() {
        @Override
        public void onBubbleClick(BubbleUI bubbleUI) {

        }

        @Override
        public void onBubbleDestroyed(BubbleUI bubbleUI, boolean isLastBubble) {

        }
    };

    public interface BubbleActivityServiceListener {
        void onBubbleClick(BubbleUI bubbleUI);
        void onBubbleDestroyed(BubbleUI bubbleUI, boolean isLastBubble);
    }

    public void addActivityListener(@NonNull BubbleActivityServiceListener listener) {
        mActivityListener = listener;
    }

    @Override
    public void onBubbleSpringPositionUpdate(int x, int y) {
        firstBubbleX = x;
        firstBubbleY = y;
        if (!linkedBubbles) return;
        mPositionUtils.forEachBubble(new PositionUtils.forLoopCallback() {
            @Override
            public void forEach(BubbleUI bubbleUI, int position) {
                bubbleUI.updateBubblePosition(firstBubbleX, firstBubbleY);
            }
        });
    }

    @Override
    public void onBubbleClick(BubbleUI bubbleUI) {
        mActivityListener.onBubbleClick(bubbleUI);
    }

    @Override
    public void onBubbleDestroyed(BubbleUI bubbleUI, boolean isLastBubble) {
        mPositionUtils.updateBubblePositions();
        mActivityListener.onBubbleDestroyed(bubbleUI, isLastBubble);
    }

    public class LocalBinder extends Binder {
        public BubbleService getInstance() {
            return BubbleService.this;
        }
    }

    public void linkBubbles() {
        linkedBubbles = true;
        updateBubbleLinkStatus();
    }

    public void unlinkBubbles() {
        linkedBubbles = false;
        updateBubbleLinkStatus();
    }

    public void updateBubbleLinkStatus() {
        mPositionUtils.forEachBubble(new PositionUtils.forLoopCallback() {
            @Override
            public void forEach(BubbleUI bubbleUI, int position) {
                bubbleUI.linkBubble(linkedBubbles);
            }
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public static BubbleService getInstance() {
        return sInstance;
    }

    public static void destroySelf() {
        if (sInstance != null) {
            sInstance.stopSelf();
        }
    }

    public boolean canDrawOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean canDraw = Settings.canDrawOverlays(this);
            L.d("CanDraw", canDraw);
            return canDraw;
        }
        return true;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                L.d("Exited webhead service since overlay permission was revoked");
                stopSelf();
                return;
            }
        }
        RemoveBubble.get(this);
        sInstance = this;
//        registerReceivers();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public void addBubble(final BubbleUI newBubble) {
        newBubble.linkBubble(linkedBubbles);
        newBubble.addServiceListener(BubbleService.this);
        newBubble.addInteractionListener(BubbleService.this);
        mPositionUtils.addBubble(newBubble);
        // Before adding new bubbles, call move self to stack distance on existing bubbles to move
        // them a little such that they appear to be stacked
        AnimatorSet animatorSet = new AnimatorSet();
        final List<Animator> animators = new LinkedList<>();
        mPositionUtils.forEachBubble(new PositionUtils.forLoopCallback() {
            @Override
            public void forEach(BubbleUI bubbleUI, int position) {
                Animator anim = bubbleUI.getStackDistanceAnimator();
                if (anim != null) animators.add(anim);
            }
        });
        animatorSet.playTogether(animators);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                //noinspection ConstantConditions
                if (newBubble != null) {
                    newBubble.reveal();
                }
            }
        });
        animatorSet.start();
    }

    @Override
    public void onDestroy() {
        L.d("Exiting Bubble service");
        mPositionUtils.destroyAllBubbles();

//        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalReceiver);
//        unregisterReceiver(mStopServiceReceiver);

        stopForeground(true);
        sInstance = null;
        RemoveBubble.destroy();
        super.onDestroy();
    }

    public void hideRemoveView() {
        RemoveBubble.disappear();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        L.d(newConfig.toString());
        // TODO handle webhead positions after orientations change.
    }

}
