package com.pitchedapps.bubble.library.services;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.CallSuper;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.facebook.rebound.Spring;
import com.facebook.rebound.SpringConfig;
import com.facebook.rebound.SpringSystem;
import com.pitchedapps.bubble.library.R;
import com.pitchedapps.bubble.library.logging.BLog;
import com.pitchedapps.bubble.library.physics.SpringChain2D;
import com.pitchedapps.bubble.library.ui.Bubble;
import com.pitchedapps.bubble.library.ui.BubbleContract;
import com.pitchedapps.bubble.library.ui.RemoveBubble;
import com.pitchedapps.bubble.library.utils.Constants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public abstract class BubbleService<T extends Bubble> extends Service implements BubbleContract<T> {

    protected abstract int maxBubbleCount();

    /**
     * Reference to all the bubbles created on screen. Ordered in the order of creation by using
     * {@link LinkedHashMap}. The key must be unique
     */
    private final Map<String, T> mBubbleMap = new LinkedHashMap<>();
    /**
     * The base spring system to create our springs.
     */
    private final SpringSystem mSpringSystem = SpringSystem.create();
    // Clubbed movement manager
    private SpringChain2D mSpringChain2D;
    // Max visible web heads is set 6 for performance reasons.
    public static final int MAX_VISIBLE_BUBBLE_COUNT = 6;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        BLog.e("Create");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                BLog.d("Exited BubbleService since overlay permission was revoked");
                stopSelf();
                return;
            }
        }
        mSpringChain2D = SpringChain2D.create(this, maxBubbleCount());
        RemoveBubble.get(this);

        // bind to custom tab session
//        bindToCustomTabSession();
        registerReceivers();

        showNotification();
    }

    @Override
    public void onDestroy() {
        BLog.d("Exiting webhead service");
        Bubble.clearMasterPosition();
        Bubble.cancelToast();

        removeWebHeads();

//        PageExtractTasksManager.cancelAll(true);
//        PageExtractTasksManager.unRegisterListener();

        unregisterReceivers();

//        if (mCustomTabManager != null) {
//            mCustomTabManager.unbindCustomTabsService(this);
//        }

        stopForeground(true);
        RemoveBubble.destroy();
        super.onDestroy();
    }

//    public static CustomTabsSession getTabSession() {
//        if (mCustomTabManager != null) {
//            return mCustomTabManager.getSession();
//        }
//        return null;
//    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        BLog.e("Start command");
        processIntent(intent);
        return START_STICKY;
    }

    public static void createBubble(Activity activity, String data, Class<? extends BubbleService> serviceClass) {
        Intent serviceIntent = new Intent(activity, serviceClass);
        serviceIntent.setData(Uri.parse(data));
        activity.startService(serviceIntent);
    }

    public static void stop(Activity activity, Class<? extends BubbleService> serviceClass) {
        activity.stopService(new Intent(activity, serviceClass));
    }

    private void showNotification() {
        final PendingIntent contentIntent = PendingIntent.getBroadcast(this,
                0,
                new Intent(Constants.ACTION_STOP_WEBHEAD_SERVICE),
                PendingIntent.FLAG_UPDATE_CURRENT);

        final Notification notification = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_chromer_notification)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentText(getString(R.string.tap_close_all))
                .setColor(ContextCompat.getColor(this, R.color.colorPrimary))
                .setContentTitle(getString(R.string.web_heads_service))
                .setContentIntent(contentIntent)
                .setAutoCancel(false)
                .setLocalOnly(true)
                .build();

        startForeground(1, notification);
    }

    private void processIntent(Intent intent) {
        if (intent == null || intent.getDataString() == null) return; // don't do anything
        if (!isAlreadyLoaded(intent.getDataString())) {
            addBubble(intent);
        } else {
            toast(R.string.already_loaded);
        }
    }

    protected void toast(@StringRes int s) {
        toast(getString(s));
    }

    protected void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private void addBubble(@NonNull final Intent intent) {
//        PageExtractTasksManager.startExtraction(webHeadUrl);
        mSpringChain2D.clear();
        final T newWebHead = createBubble(intent);
        mSpringChain2D.setMasterSprings(newWebHead.getXSpring(), newWebHead.getYSpring());

        int index = mBubbleMap.values().size();
        for (T oldWebHead : mBubbleMap.values()) {
            oldWebHead.setMaster(false);
            if (shouldQueue(index + 1)) {
                oldWebHead.setInQueue(true);
            } else {
                oldWebHead.setSpringConfig(SpringConfig.fromOrigamiTensionAndFriction(90, 9 + (index * 5)));
                mSpringChain2D.addSlaveSprings(oldWebHead.getXSpring(), oldWebHead.getYSpring());
            }
            index--;
        }
        mSpringChain2D.rest();

        newWebHead.reveal();
        mBubbleMap.put(intent.getDataString(), newWebHead);

    }

    protected abstract T createBubble(@NonNull final Intent intent);

    private boolean shouldQueue(int index) {
        return index > MAX_VISIBLE_BUBBLE_COUNT;
    }

//    @Override
//    public void onUrlUnShortened(String originalUrl, String unShortenedUrl) {
//        // First check if the associated web head is active
//        final WebHead webHead = mBubbleMap.get(originalUrl);
//        if (webHead != null) {
//            webHead.setUnShortenedUrl(unShortenedUrl);
//            if (!Preferences.aggressiveLoading(this)) {
//                if (mCustomTabConnected)
//                    mCustomTabManager.mayLaunchUrl(Uri.parse(unShortenedUrl), null, getPossibleUrls());
//                else
//                    deferMayLaunchUntilConnected(unShortenedUrl);
//            }
//        }
//    }

//    @Override
//    public void onUrlExtracted(String originalUrl, @Nullable JResult result) {
//        final WebHead webHead = mBubbleMap.get(originalUrl);
//        if (webHead != null && result != null) {
//            try {
//                final String faviconUrl = result.getFaviconUrl();
//                webHead.setTitle(result.getTitle());
//                webHead.setFaviconUrl(faviconUrl);
//                Glide.with(this)
//                        .load(faviconUrl)
//                        .asBitmap()
//                        .into(new BitmapImageViewTarget(webHead.getFaviconView()) {
//                            @Override
//                            public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
//                                if (resource == null) {
//                                    return;
//                                }
//                                // dispatch color extraction task
//                                new ColorExtractionTask(webHead, resource).execute();
//
//                                final RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(getResources(), resource);
//                                roundedBitmapDrawable.setAntiAlias(true);
//                                roundedBitmapDrawable.setCircular(true);
//                                webHead.setFaviconDrawable(roundedBitmapDrawable);
//                            }
//                        });
//                // Also signal the context activity so that it can update its data
//                ContextActivityHelper.signalUpdated(this, webHead.getWebsite());
//            } catch (Exception e) {
//                BLog.e(e.getMessage());
//            }
//        }
//    }

    //Check if unique key is already used
    private boolean isAlreadyLoaded(@Nullable String key) {
        return key == null || mBubbleMap.containsKey(key);
    }

    private void removeWebHeads() {
        for (Bubble webhead : mBubbleMap.values()) {
            if (webhead != null) webhead.destroySelf(false);
        }
        // Since no callback is received clear the map manually.
        mBubbleMap.clear();
        BLog.d("WebHeads: %d", mBubbleMap.size());
    }

    private void updateWebHeadColors(@ColorInt int webHeadColor) {
        final AnimatorSet animatorSet = new AnimatorSet();
        List<Animator> animators = new LinkedList<>();
        for (Bubble webhead : mBubbleMap.values()) {
            animators.add(webhead.getRevealAnimator(webHeadColor));
        }
        animatorSet.playTogether(animators);
        animatorSet.start();
    }

    private void updateSpringChain() {
        mSpringChain2D.rest();
        mSpringChain2D.clear();
        mSpringChain2D.enableDisplacement();
        // Index that is used to differentiate spring config
        int springChainIndex = mBubbleMap.values().size();
        // Index that is used to determine if the web hed should be in queue.
        int index = mBubbleMap.values().size();
        for (Bubble webHead : mBubbleMap.values()) {
            if (webHead != null) {
                if (webHead.isMaster()) {
                    // Master will never be in queue, so no check is made.
                    mSpringChain2D.setMasterSprings(webHead.getXSpring(), webHead.getYSpring());
                } else {
                    if (shouldQueue(index)) {
                        webHead.setInQueue(true);
                    } else {
                        webHead.setInQueue(false);
                        // We should add the springs to our chain only if the web head is active
                        webHead.setSpringConfig(SpringConfig.fromOrigamiTensionAndFriction(90, 9 + (springChainIndex * 5)));
                        mSpringChain2D.addSlaveSprings(webHead.getXSpring(), webHead.getYSpring());
                    }
                    springChainIndex--;
                }
                index--;
            }
        }
    }

    private void selectNextMaster() {
        final ListIterator<String> it = new ArrayList<>(mBubbleMap.keySet()).listIterator(mBubbleMap.size());
        //noinspection LoopStatementThatDoesntLoop
        while (it.hasPrevious()) {
            final String key = it.previous();
            final Bubble toBeMaster = mBubbleMap.get(key);
            if (toBeMaster != null) {
                toBeMaster.setMaster(true);
                updateSpringChain();
                toBeMaster.goToMasterTouchDownPoint();
            }
            break;
        }
    }

    @Override
    public final void onBubbleClick(@NonNull T bubble) {
        hideRemoveView();
        if (onClick(bubble)) bubble.destroySelf(true);;
    }

    /**
     * Nested abstract callback for bubble click
     * @param bubble bubble that was clicked
     * @return true to destroy the bubble; false to keep it
     */
    public abstract boolean onClick(@NonNull T bubble);

    @Override
    @CallSuper
    public void onBubbleDestroyed(@NonNull Bubble bubble, boolean isLastBubble) {
        bubble.setMaster(false);
        mBubbleMap.remove(bubble.getKey());

        if (isLastBubble) {
            RemoveBubble.get(this).destroyAnimator(new Runnable() {
                @Override
                public void run() {
                    stopSelf();
                }
            });
        } else {
            selectNextMaster();
            // Now that this web head is destroyed, with this web head as the reference prepare the
            // other urls
//            prepareNextSetOfUrls(bubble.getUrl());
        }
//        ContextActivityHelper.signalDeleted(this, bubble.getWebsite());
    }

    @Override
    public void onMasterBubbleMoved(int x, int y) {
        mSpringChain2D.performGroupMove(x, y);
    }

    @NonNull
    @Override
    public Spring newSpring() {
        return mSpringSystem.createSpring();
    }

    @Override
    public void onMasterLockedToRemove() {
        mSpringChain2D.disableDisplacement();
    }

    @Override
    public void onMasterReleasedFromRemove() {
        mSpringChain2D.enableDisplacement();
    }

    @Override
    public void closeAll() {
        stopSelf();
    }

    @Override
    public void onMasterLongClick() {
//        final ListIterator<String> it = new ArrayList<>(mBubbleMap.keySet()).listIterator(mBubbleMap.size());
//        ArrayList<WebSite> webSites = new ArrayList<>();
//        while (it.hasPrevious()) {
//            final String key = it.previous();
//            final Bubble bubble = mBubbleMap.get(key);
//            if (bubble != null) {
//                webSites.add(bubble.getWebsite());
//            }
//        }
//        ContextActivityHelper.open(this, webSites);
    }

    private void closeBubbleByKey(String key) {
        final Bubble bubble = mBubbleMap.get(key);
        if (bubble != null) {
            bubble.destroySelf(true);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        BLog.d(newConfig.toString());
        // TODO handle bubble positions after orientations change.
    }

    private void hideRemoveView() {
        RemoveBubble.disappear();
    }

    private void registerReceivers() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Constants.ACTION_WEBHEAD_COLOR_SET);
        filter.addAction(Constants.ACTION_REBIND_WEBHEAD_TAB_CONNECTION);
        filter.addAction(Constants.ACTION_CLOSE_WEBHEAD_BY_URL);
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocalReceiver, filter);
        registerReceiver(mStopServiceReceiver, new IntentFilter(Constants.ACTION_STOP_WEBHEAD_SERVICE));
    }

    private void unregisterReceivers() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalReceiver);
        unregisterReceiver(mStopServiceReceiver);
    }

    private final BroadcastReceiver mLocalReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case Constants.ACTION_REBIND_WEBHEAD_TAB_CONNECTION:
                    final boolean shouldRebind = intent.getBooleanExtra(Constants.EXTRA_KEY_REBIND_WEBHEAD_CXN, false);
//                    if (shouldRebind) {
//                        bindToCustomTabSession();
//                    }
                    break;
                case Constants.ACTION_WEBHEAD_COLOR_SET:
                    final int webHeadColor = intent.getIntExtra(Constants.EXTRA_KEY_WEBHEAD_COLOR, Constants.NO_COLOR);
                    if (webHeadColor != Constants.NO_COLOR) {
                        updateWebHeadColors(webHeadColor);
                    }
                    break;
                case Constants.ACTION_CLOSE_WEBHEAD_BY_URL:
//                    final WebSite webSite = intent.getParcelableExtra(Constants.EXTRA_KEY_WEBSITE);
//                    if (webSite != null) {
//                        closeWebHeadByUrl(webSite.url);
//                    }
                    break;
            }
        }
    };

    private final BroadcastReceiver mStopServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopSelf();
        }
    };

}