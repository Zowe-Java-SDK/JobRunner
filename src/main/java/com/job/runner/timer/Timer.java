package com.job.runner.timer;

/**
 * Timer class to help support wait time operations.
 *
 * @author Frank Giordano
 */
public class Timer {

    /**
     * Wait by time specified in milliseconds.
     */
    private final int waitTime;

    /**
     * End by time specified in milliseconds.
     */
    private long endTime;

    /**
     * Timer constructor
     *
     * @param waitTime in milliseconds
     */
    public Timer(final int waitTime) {
        this.waitTime = waitTime;
        this.endTime = System.currentTimeMillis();
    }

    /**
     * Initialize time construct before calling isEnded
     *
     * @return timer object
     */
    public Timer initialize() {
        final long currentTime = System.currentTimeMillis();
        endTime = currentTime + waitTime;
        return this;
    }

    /**
     * Has the current time range ended yet.
     *
     * @return boolean true if time range reached
     */
    public boolean isEnded() {
        return System.currentTimeMillis() >= endTime;
    }

}
