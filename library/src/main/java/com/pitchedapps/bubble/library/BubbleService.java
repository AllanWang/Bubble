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

import com.pitchedapps.bubble.library.ui.BubbleUI;
import com.pitchedapps.bubble.library.ui.RemoveItem;
import com.pitchedapps.bubble.library.utils.L;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by Allan Wang on 2016-07-09.
 */
public class BubbleService extends Service {

    protected Context mContext;
    private final Map<String, BubbleUI> mItems = new LinkedHashMap<>();
    private IBinder mBinder = new LocalBinder();
    private static BubbleService sInstance;

    public class LocalBinder extends Binder {
        public BubbleService getInstance() {
            return BubbleService.this;
        }
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
        RemoveItem.get(this);
        sInstance = this;
//        registerReceivers();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public void addItem(final BubbleUI newItem) {
        mItems.put(newItem.key, newItem);
        // Before adding new items, call move self to stack distance on existing items to move
        // them a little such that they appear to be stacked
        AnimatorSet animatorSet = new AnimatorSet();
        List<Animator> animators = new LinkedList<>();
        for (BubbleUI item : mItems.values()) {
            Animator anim = item.getStackDistanceAnimator();
            if (anim != null) animators.add(anim);
        }
        animatorSet.playTogether(animators);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                //noinspection ConstantConditions
                if (newItem != null) newItem.reveal();
            }
        });
        animatorSet.start();
    }

    public boolean isKeyAlreadyUsed(String key) {
        return key == null || mItems.containsKey(key);
    }

    public void removeItem(String key) {
        if (!mItems.containsKey(key)) return;
        mItems.remove(key);
    }

    public void destroyAllItems() {
        for (BubbleUI item : mItems.values()) {
            if (item != null) item.destroySelf(false);
        }
        // Since no callback is received clear the map manually.
        mItems.clear();
        L.d("Items: %d", mItems.size());
    }

    @Override
    public void onDestroy() {
        L.d("Exiting Bubble service");
        destroyAllItems();

//        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalReceiver);
//        unregisterReceiver(mStopServiceReceiver);

        stopForeground(true);
        sInstance = null;
        RemoveItem.destroy();
        super.onDestroy();
    }

    public void hideRemoveView() {
        RemoveItem.disappear();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        L.d(newConfig.toString());
        // TODO handle webhead positions after orientations change.
    }

}
