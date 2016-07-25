package com.pitchedapps.bubble.library.utils;

import android.support.annotation.NonNull;

import com.pitchedapps.bubble.library.ui.BubbleUI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Allan Wang on 2016-07-25.
 */
public class PositionUtils {

    private List<String> mList = new ArrayList<>();
    private HashMap<String, BubbleUI> mMap = new HashMap<>();

    public PositionUtils() {

    }


    public interface forLoopCallback {
        void forEach(BubbleUI bubbleUI, int position);
    }

    /**
     * Callback function for every item in the list
     *
     * @param loop
     */
    public void forEachBubble(@NonNull forLoopCallback loop) {
        for (int i = 0; i < mList.size(); i++) {
            BubbleUI bubbleUI = getBubble(i);
            loop.forEach(bubbleUI, i);
        }
    }

    public void updateBubblePositions() {
        forEachBubble(new forLoopCallback() {
            @Override
            public void forEach(BubbleUI bubbleUI, int position) {
                bubbleUI.setPosition(position);
            }
        });
    }

    public void addBubble(BubbleUI bubbleUI) {
        if (!isKeyAlreadyUsed(bubbleUI.key)) {
            mList.add(0, bubbleUI.key);
            mMap.put(bubbleUI.key, bubbleUI);
            updateBubblePositions();
        } else {
            mMap.put(bubbleUI.key, bubbleUI);
            L.d("Update bubble with key ", bubbleUI.key);
        }
    }

    public BubbleUI getBubble(String key) {
        if (!isKeyAlreadyUsed(key)) return null;
        if (!mMap.containsKey(key)) {
            L.e("Error with key", key);
            return null;
        }
        return mMap.get(key);
    }

    public BubbleUI getBubble(int position) {
        if (position >= mList.size()) return null;
        return getBubble(mList.get(position));
    }

    public boolean removeBubble(String key) {
        if (!isKeyAlreadyUsed(key)) return false;
        BubbleUI bubbleUI = getBubble(key);
        if (bubbleUI != null) bubbleUI.destroySelf(false);
        mMap.remove(key);
        return true;
    }

    public boolean removeBubble(int position) {
        if (position >= mList.size()) ;
        return removeBubble(mList.get(position));
    }

    public void destroyAllBubbles() {
        forEachBubble(new forLoopCallback() {
            @Override
            public void forEach(BubbleUI bubbleUI, int position) {
                bubbleUI.destroySelf(false);
            }
        });
        mList.clear();
        mMap.clear();
    }

    public boolean isKeyAlreadyUsed(String key) {
        return key == null || mList.contains(key);
    }
}
