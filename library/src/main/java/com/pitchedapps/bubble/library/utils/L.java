package com.pitchedapps.bubble.library.utils;

import android.util.Log;

/**
 * Created by Allan Wang on 2016-06-28.
 */
public class L {
    public static void e(Object... o) {
        String ss = "";
        for (Object oo : o) {
            ss += " " + String.valueOf(oo);
        }
        Log.e("BubbleService", ss);
    }

    public static void d(Object... o) {
        String ss = "";
        for (Object oo : o) {
            ss += " " + String.valueOf(oo);
        }
        Log.d("BubbleService", ss);
    }
}
