package com.pitchedapps.bubble.library.logging;

import timber.log.Timber;

/**
 * Created by Allan Wang on 2016-12-20.
 */

public class BLog {

    public static BubbleLogTree logTree;
    public static final int DEBUG = 111, ERROR = 666;

    public static void d(String message, Object... o) {
        Timber.log(DEBUG, message, o);
    }

    public static void e(String message, Object... o) {
        Timber.log(ERROR, message, o);
    }

    public static void enable() {
        if (logTree == null) {
            logTree = new BubbleLogTree();
            Timber.plant(logTree);
        }
    }

    public static void disable() {
        if (logTree != null) {
            Timber.uproot(logTree);
            logTree = null;
        }
    }
}
