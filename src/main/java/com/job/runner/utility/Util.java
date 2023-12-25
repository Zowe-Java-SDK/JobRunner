package com.job.runner.utility;

import zowe.client.sdk.rest.Response;

/**
 * Utility class with helper method(s).
 *
 * @author Frank Giordano
 */
public final class Util {

    /**
     * Return a string message value from Response object
     *
     * @param response Response object
     * @return string value
     */
    public static String getResponsePhrase(Response response) {
        if (response == null || response.getResponsePhrase().isEmpty()) {
            return null;
        }
        return response.getResponsePhrase().get().toString();
    }

}
