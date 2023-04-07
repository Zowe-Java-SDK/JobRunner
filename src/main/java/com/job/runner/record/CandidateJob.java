package com.job.runner.record;

/**
 * Stores data record of a job to be submitted.
 *
 * @param dataset dataset location where member exist
 * @param member  member name that exist in dataset location
 * @param acctNum account number of the user submitted job, use to fill in job card
 * @param ssid    ssid of the sysplex to submit job under, use to fill in job card, optional field
 * @author Frank Giordano
 */
public record CandidateJob(String dataset, String member, String acctNum, String ssid) {
}

