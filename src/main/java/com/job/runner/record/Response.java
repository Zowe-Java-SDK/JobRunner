package com.job.runner.record;

/**
 * Stores data record of a submitted job's response info.
 *
 * @param message   response message, contains information on job status after submitting
 * @param isSuccess indicates submitted job success status
 * @author Frank Giordano
 */
public record Response(String message, boolean isSuccess) {
}
