package com.pitchedapps.bubble.library.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.pitchedapps.bubble.library.ui.BubbleUI;
import com.pitchedapps.bubble.library.utils.L;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Allan Wang on 2016-07-25.
 *
 * Service component that contains all the bubbles and positions
 */
public class BaseService extends Service{

    protected List<String> mList = new ArrayList<>();
    protected HashMap<String, BubbleUI> mMap = new HashMap<>();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void updateBubblePositions() {
        for (int i = 0; i < mList.size(); i++) {
            getBubble(i).setPosition(i);
        }
    }

    /**
     * Portion of addBubble that adds it to the stored lists
     *
     * @param bubbleUI bubble to add
     */
    public void addBubbleToList(BubbleUI bubbleUI) {
        if (!isKeyAlreadyUsed(bubbleUI.getKey())) {
            mList.add(0, bubbleUI.getKey());
            mMap.put(bubbleUI.getKey(), bubbleUI);
            updateBubblePositions();
        } else {
            mMap.put(bubbleUI.getKey(), bubbleUI);
            L.d("Update bubble with key ", bubbleUI.getKey());
        }
    }

    public BubbleUI getBubble(@NonNull String key) {
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

    public boolean removeBubble(@NonNull String key) {
        if (!isKeyAlreadyUsed(key)) return false;
        BubbleUI bubbleUI = getBubble(key);
        if (bubbleUI != null) bubbleUI.destroySelf(false);
        mList.remove(mList.indexOf(key)); //TODO check if valid
        mMap.remove(key);
        updateBubblePositions();
        return true;
    }

    public boolean removeBubble(int position) {
        return position < mList.size() && removeBubble(mList.get(position));
    }

    public void destroyAllBubbles() {
        for (BubbleUI bubbleUI : mMap.values()) {
            bubbleUI.destroySelf(false);
        }
        mList.clear();
        mMap.clear();
    }

    public boolean isKeyAlreadyUsed(String key) {
        return key == null || mList.contains(key);
    }
}
