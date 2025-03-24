package com.job.runner.record;

/**
 * Stores data record of a submitted job's response info.
 *
 * @param message response message, contains information on job status after submitting
 * @param status  indicates submitted job execution status
 * @author Frank Giordano
 */
public record Response(String message, boolean status) {

    public boolean isSuccess() {
        return status;
    }

    public boolean isFailed() {
        return !status;
    }

}
