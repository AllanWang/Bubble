package com.pitchedapps.bubble.library.item;

import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;

import com.pitchedapps.bubble.library.utils.ColourUtils;

/**
 * Created by Allan Wang on 2016-12-20.
 */

public class BubbleStyle {
    private int backgroundColor = ColourUtils.randomDarkColor();
    private String indicatorText = null;
    private Drawable iconDrawable = null;

    public BubbleStyle setBackgroundColor(@ColorInt int backgroundColor) {
        if (backgroundColor == -1) return this;
        this.backgroundColor = backgroundColor;
        return this;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public BubbleStyle setIndicatorText(@NonNull String s) {
        indicatorText = s;
        return this;
    }

    public String getIndicatorText() {
        return indicatorText;
    }

    public BubbleStyle setIconDrawable(@NonNull Drawable drawable) {
        iconDrawable = drawable;
        return this;
    }

    public Drawable getIconDrawable() {
        return iconDrawable;
    }
}
