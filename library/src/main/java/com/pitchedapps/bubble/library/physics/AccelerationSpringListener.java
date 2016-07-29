package com.pitchedapps.bubble.library.physics;

import com.facebook.rebound.Spring;
import com.facebook.rebound.SpringListener;
import com.pitchedapps.bubble.library.utils.L;

/**
 * Created by Allan Wang on 2016-07-28.
 */
public class AccelerationSpringListener implements SpringListener {

    private double acceleration;
    private long prevTime;

    public AccelerationSpringListener(double acceleration) {
        this.acceleration = acceleration;
        prevTime = System.currentTimeMillis();
        L.e("ADD ASL");
    }


    @Override
    public void onSpringUpdate(Spring spring) {
        long timeDiff = System.currentTimeMillis() - prevTime;
        prevTime += timeDiff;
        double extraVelocity = acceleration * timeDiff / 1000;
        double velocity = spring.getVelocity();
        velocity = velocity > 0 ? velocity + extraVelocity : velocity - extraVelocity;
        spring.setVelocity(velocity);
    }

    @Override
    public void onSpringAtRest(Spring spring) {
        spring.removeListener(this);
        L.e("REMOVE ASL");
    }

    @Override
    public void onSpringActivate(Spring spring) {

    }

    @Override
    public void onSpringEndStateChange(Spring spring) {

    }
}
