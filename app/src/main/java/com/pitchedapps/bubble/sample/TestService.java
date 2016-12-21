package com.pitchedapps.bubble.sample;

import android.content.Intent;
import android.support.annotation.NonNull;

import com.pitchedapps.bubble.library.logging.BLog;
import com.pitchedapps.bubble.library.services.BubbleService;
import com.pitchedapps.bubble.library.ui.Bubble;

/**
 * Created by Allan Wang on 2016-12-20.
 */

public class TestService extends BubbleService {
    @Override
    protected int maxBubbleCount() {
        return 5;
    }

    @Override
    protected Bubble createBubble(@NonNull Intent intent) {
        return new TestBubble(this, intent.getDataString(), this);
    }

    /**
     * Nested abstract callback for bubble click
     *
     * @param bubble bubble that was clicked
     * @return true to destroy the bubble; false to keep it
     */
    @Override
    public boolean onClick(@NonNull Bubble bubble) {
        BLog.d("Click " + bubble.getKey());
        return false;
    }


}
