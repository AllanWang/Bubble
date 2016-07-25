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

import com.facebook.rebound.Spring;
import com.pitchedapps.bubble.library.ui.BubbleUI;
import com.pitchedapps.bubble.library.ui.RemoveBubble;
import com.pitchedapps.bubble.library.utils.L;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by Allan Wang on 2016-07-09.
 */
public class BubbleService extends Service implements BubbleUI.BubbleUIServiceListener{

    protected Context mContext;
    private final Map<String, BubbleUI> mBubbles = new LinkedHashMap<>();
    private IBinder mBinder = new LocalBinder();
    private static BubbleService sInstance;
    private static boolean linkedBubbles = false;

    @Override
    public void onBubbleSpringUpdate(Spring spring) {
        if (!linkedBubbles) return;
        for (BubbleUI bubble : mBubbles.values()) {
            bubble.updateSpring(spring);
        }
    }

    public class LocalBinder extends Binder {
        public BubbleService getInstance() {
            return BubbleService.this;
        }
    }

    public void linkBubbles() {
        linkedBubbles = true;
    }

    public void unlinkBubbles() {
        linkedBubbles = false;
    }

    public void updateBubbleLinkStatus() {
        for (BubbleUI bubble : mBubbles.values()) {
            bubble.IS_LINKED_AND_FIRST = false;
        }
        getFirstBubble().IS_LINKED_AND_FIRST = linkedBubbles;
    }

    public BubbleUI getFirstBubble() {
        String key = mBubbles.keySet().iterator().next();
        return mBubbles.get(key);
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
        mBubbles.put(newBubble.key, newBubble);
        // Before adding new bubbles, call move self to stack distance on existing bubbles to move
        // them a little such that they appear to be stacked
        AnimatorSet animatorSet = new AnimatorSet();
        List<Animator> animators = new LinkedList<>();
        for (BubbleUI bubble : mBubbles.values()) {
            Animator anim = bubble.getStackDistanceAnimator();
            if (anim != null) animators.add(anim);
        }
        animatorSet.playTogether(animators);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                //noinspection ConstantConditions
                if (newBubble != null) {
                    newBubble.reveal();
                    newBubble.addServiceListener(BubbleService.this);
                    newBubble.IS_LINKED_AND_FIRST = linkedBubbles;
                }
            }
        });
        animatorSet.start();
    }

    public boolean isKeyAlreadyUsed(String key) {
        return key == null || mBubbles.containsKey(key);
    }

    public void removeBubble(String key) {
        if (!mBubbles.containsKey(key)) return;
        mBubbles.remove(key);
    }

    public void destroyAllBubbles() {
        for (BubbleUI bubble : mBubbles.values()) {
            if (bubble != null) bubble.destroySelf(false);
        }
        // Since no callback is received clear the map manually.
        mBubbles.clear();
        L.d("Bubbles: %d", mBubbles.size());
    }

    public int getBubbleCount() {
        return mBubbles.size();
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
