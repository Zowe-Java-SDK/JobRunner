package com.job.runner.timer;

/**
 * Global Utility Class with static helper methods.
 *
 * @author Frank Giordano
 */
public final class WaitUtil {

    /**
     * Private constructor defined to avoid instantiation of class
     */
    private WaitUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Wait by time specified.
     *
     * @param time in milliseconds
     */
    public static void wait(final int time) {
        final Timer timer = new Timer(time).initialize();
        while (true) {
            if (timer.isEnded()) {
                break;
            }
        }
    }

}
