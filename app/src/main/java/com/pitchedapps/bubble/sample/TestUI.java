package com.pitchedapps.bubble.sample;

import android.animation.Animator;
import android.content.Context;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.pitchedapps.bubble.library.ui.BubbleUI;

/**
 * Created by Allan Wang on 2016-07-24.
 */
public class TestUI extends BubbleUI {

    /**
     * Inits the web head and attaches to the system window. It is assumed that draw over other apps permission is
     * granted for 6.0+.
     *
     * @param context  Service
     * @param key      Key for bubble for the linkedhashmap
     */
    public TestUI(@NonNull Context context, @NonNull String key) {
        super(context, key);
    }

    @NonNull
    @Override
    protected FrameLayout inflateContent(Context context) {
        return getFrameLayoutFromID(R.layout.web_head_layout);
    }

    @NonNull
    @Override
    protected ImageView setImageView(FrameLayout frameLayout) {
        return getImageViewFromID(R.id.test);
    }

    @Override
    protected void setViewElevations(int px) {

    }

    @Override
    protected Animator getRevealInAnimator(@ColorInt int newIconColor) {
        return null;
    }
}
