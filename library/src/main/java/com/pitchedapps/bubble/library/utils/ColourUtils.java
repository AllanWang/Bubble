package com.pitchedapps.bubble.library.utils;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.v4.graphics.ColorUtils;

import java.util.Random;

/**
 * Created by Arun on 12/06/2016.
 */
public class ColourUtils {

    private static double colorDifference(@ColorInt int a, @ColorInt int b) {
        double aLab[] = new double[3];
        double bLab[] = new double[3];
        ColorUtils.colorToLAB(a, aLab);
        ColorUtils.colorToLAB(b, bLab);
        return ColorUtils.distanceEuclidean(aLab, bLab);
    }

    /**
     * Returns white or black based on color luminance
     *
     * @param backgroundColor the color to get foreground for
     * @return White for darker colors and black for ligher colors
     */
    @ColorInt
    public static int getForegroundWhiteOrBlack(@ColorInt int backgroundColor) {
        double l = ColorUtils.calculateLuminance(backgroundColor);
        if (l > 0.179) {
            return Color.BLACK;
        } else
            return Color.WHITE;
    }


    @NonNull
    public static Drawable getRippleDrawableCompat(final @ColorInt int color) {
        if (Utils.isLollipopAbove()) {
            return new RippleDrawable(ColorStateList.valueOf(color),
                    null,
                    null
            );
        }
        int translucentColor = ColorUtils.setAlphaComponent(color, 0x44);
        StateListDrawable stateListDrawable = new StateListDrawable();
        int[] states = new int[]{android.R.attr.state_pressed};
        stateListDrawable.addState(states, new ColorDrawable(translucentColor));
        return stateListDrawable;
    }

    public static int randomDarkColor() {
        Random rnd = new Random();
        float r = rnd.nextInt(255);
        float g = rnd.nextInt(255);
        float b = rnd.nextInt(255);
        r *= 0.1f;
        g *= 0.1f;
        b *= 0.1f;
        return Color.rgb((int) r, (int) g, (int) b);
    }
}
