package com.pitchedapps.bubble.sample;

import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.pitchedapps.bubble.library.BubbleActivity;
import com.pitchedapps.bubble.library.services.BubbleService;
import com.pitchedapps.bubble.library.ui.BubbleUI;

import java.util.Random;

public class MainActivity extends BubbleActivity implements BubbleService.BubbleActivityServiceListener {

    private int i = 0;

    @Override
    protected void onServiceFirstRun() {
        mBubbleService.linkBubbles();
        mBubbleService.addActivityListener(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                if (mBounded) {
                    TestUI testUI = new TestUI(MainActivity.this, String.valueOf(i));
                    mBubbleService.addBubble(testUI);
                    i++;
                }
//                launchCustomTab();
//                BubbleService.StartFromActivity(MainActivity.this);

            }
        });
    }

//    private void launchCustomTab() {
//        final Intent webHeadService = new Intent(this, TestService.class);
//        webHeadService.setData(Uri.parse("https://www.google.ca"));
//        startService(webHeadService);
//    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBubbleClick(BubbleUI bubbleUI) {
        Random rnd = new Random();
        int color = Color.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256));
        bubbleUI.mIcon.setBackgroundColor(color);
    }

    @Override
    public void onBubbleDestroyed(BubbleUI bubbleUI, boolean isLastBubble) {

    }
}
