package com.pitchedapps.bubble.library;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.CallSuper;
import android.support.v7.app.AppCompatActivity;

import com.pitchedapps.bubble.library.ui.BubbleUI;
import com.pitchedapps.bubble.library.utils.L;

/**
 * Created by Allan Wang on 2016-07-24.
 */
public class BubbleActivity extends AppCompatActivity {
    protected boolean mBounded = false;
    protected BubbleService mBubbleService;
    private boolean serviceFirstRun = true;

    protected void startBubbleService() {
        Intent intent = new Intent(this, BubbleService.class);
        startService(intent);
    }

    protected void bindBubbleService() {
        if (!mBounded) {
            Intent intent = new Intent(this, BubbleService.class);
            bindService(intent, mConnection, BIND_AUTO_CREATE);
            mBounded = true;
        }
    }

    protected void unbindBubbleService() {
        if (mBounded) {
            unbindService(mConnection);
            mBounded = false;
        }
    }

    protected void onServiceFirstRun() {

    }

    ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBounded = false;
            mBubbleService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBounded = true;
            BubbleService.LocalBinder mLocalBinder = (BubbleService.LocalBinder) service;
            mBubbleService = mLocalBinder.getInstance();
            if (serviceFirstRun) {
                onServiceFirstRun();
                serviceFirstRun = false;
            }
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        startBubbleService();
        bindBubbleService();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindBubbleService();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BubbleService.destroySelf();
    }
}
