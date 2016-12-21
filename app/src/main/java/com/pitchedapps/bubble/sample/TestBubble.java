package com.pitchedapps.bubble.sample;

import android.content.Context;
import android.support.annotation.NonNull;
import android.widget.FrameLayout;

import com.pitchedapps.bubble.library.item.BubbleStyle;
import com.pitchedapps.bubble.library.ui.Bubble;
import com.pitchedapps.bubble.library.ui.BubbleContract;

/**
 * Created by Allan Wang on 2016-12-20.
 */

public class TestBubble extends Bubble {


    /**
     * Inits the web head and attaches to the system window. It is assumed that draw over other apps permission is
     * granted for 6.0+.
     *
     * @param context  Service
     * @param key      Bubble Key
     * @param contract for communicating to events on the bubble
     */
    public TestBubble(@NonNull Context context, @NonNull String key, @NonNull BubbleContract contract) {
        super(context, key, contract);
    }

    @Override
    protected void postInflateContent(@NonNull Context context, @NonNull FrameLayout mFrame) {

    }

    /**
     * A way for you to customize the bubble colours and text before loading
     * This cannot be null, but you can just pass the default new BubbleStyle() for the default colours
     * Default colour is a random dark colour
     * Default indicator is the first letter of the key
     *
     * @param context view context
     * @param key     bubble key
     * @return BubbleStyle
     */
    @NonNull
    @Override
    protected BubbleStyle styleBubble(Context context, String key) {
        return new BubbleStyle();
    }


}
