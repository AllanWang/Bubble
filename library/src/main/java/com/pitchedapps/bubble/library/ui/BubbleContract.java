package com.pitchedapps.bubble.library.ui;

import android.support.annotation.NonNull;

import com.facebook.rebound.Spring;

/**
 * Created by Arun on 08/08/2016.
 */
public interface BubbleContract<T extends Bubble> {
    void onBubbleClick(@NonNull T bubble);

    void onBubbleDestroyed(@NonNull Bubble bubble, boolean isLastBubble);

    void onMasterBubbleMoved(int x, int y);

    @NonNull
    Spring newSpring();

    void onMasterLockedToRemove();

    void onMasterReleasedFromRemove();

    void closeAll();

    void onMasterLongClick();
}
