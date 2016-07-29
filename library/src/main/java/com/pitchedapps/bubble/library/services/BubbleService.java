package com.pitchedapps.bubble.library.services;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.NonNull;

import com.pitchedapps.bubble.library.ui.BubbleUI;
import com.pitchedapps.bubble.library.ui.RemoveBubble;
import com.pitchedapps.bubble.library.utils.L;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by Allan Wang on 2016-07-09.
 */
public class BubbleService extends BaseService implements BubbleUI.BubbleUIServiceListener, BubbleUI.BubbleInteractionListener {

    protected Context mContext;
    private IBinder mBinder = new LocalBinder();
    private static BubbleService sInstance;
    private static boolean linkedBubbles = false;
    private int firstBubbleX = -1, firstBubbleY = -1;
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
        //Excecute in order
        for (int i = 0; i < mList.size(); i++) {
            getBubble(i).updateBubblePosition(firstBubbleX, firstBubbleY);
        }
    }

    @Override
    public void onBubbleClick(BubbleUI bubbleUI) {
        mActivityListener.onBubbleClick(bubbleUI);
    }

    @Override
    public void onBubbleDestroyed(BubbleUI bubbleUI, boolean isLastBubble) {
        if (linkedBubbles) {
            destroyAllBubbles();
            return;
        }
        removeBubble(bubbleUI.getKey());
        updateBubblePositions();
        mActivityListener.onBubbleDestroyed(bubbleUI, isLastBubble);
    }

    public class LocalBinder extends Binder {
        public BubbleService getInstance() {
            return BubbleService.this;
        }
    }

    public void setLinkedBubbles(boolean b) {
        linkedBubbles = b;
        for (BubbleUI bubbleUI : mMap.values()) {
            bubbleUI.linkBubble(linkedBubbles, firstBubbleX, firstBubbleY);
        }
    }

    public boolean areBubblesLinked() {
        return linkedBubbles;
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
            sInstance = null;
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
        if (!canDrawOverlay()) {
            L.d("Exited webhead service since overlay permission was revoked");
            stopSelf();
            return;
        }
        RemoveBubble.get(this);
        sInstance = this;
//        registerReceivers();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public void addBubble(@NonNull final BubbleUI newBubble) {
        newBubble.linkBubbleStart(linkedBubbles, firstBubbleX, firstBubbleY);
        newBubble.addServiceListener(BubbleService.this);
        newBubble.addInteractionListener(BubbleService.this);
        newBubble.addToWindow(); //TODO add to view after all the needed variables are given to the Bubble
        addBubbleToList(newBubble);
        // Before adding new bubbles, call move self to stack distance on existing bubbles to move
        // them a little such that they appear to be stacked
        AnimatorSet animatorSet = new AnimatorSet();
        final List<Animator> animators = new LinkedList<>();
        for (BubbleUI bubbleUI : mMap.values()) {
            Animator anim = bubbleUI.getStackDistanceAnimator();
            if (anim != null) animators.add(anim);
        }
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
        destroyAllBubbles();

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
