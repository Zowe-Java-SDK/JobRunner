package com.job.runner;

import com.job.runner.record.CandidateJob;
import com.job.runner.record.Response;
import com.job.runner.submit.FutureSubmit;
import com.job.runner.utility.Util;
import zowe.client.sdk.core.ZosConnection;
import zowe.client.sdk.rest.exception.ZosmfRequestException;
import zowe.client.sdk.utility.ValidateUtils;
import zowe.client.sdk.zosfiles.dsn.input.ListParams;
import zowe.client.sdk.zosfiles.dsn.methods.DsnList;
import zowe.client.sdk.zosfiles.dsn.response.Member;
import zowe.client.sdk.zosfiles.dsn.types.AttributeType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Following program provides a way to define a set of jobs to be submitted together in like a multi batch process on
 * your z/OS instance(s). It can be used to automate a set of jobs to be submitted on a scheduled bases using for
 * instance Jenkins. Each job is submitted in an asynchronous way. As such, these jobs are submitted via a thread pool.
 * The advantage of a thread pool speeds up execution and completion time.
 *
 * @author Frank Giordano
 */
public class JobRunner {

    /**
     * Time out value for each thread task processing for a job submission.
     */
    private static final int TIMEOUT = 300;
    /**
     * Number of threads for thread pool used for submitting each job.
     */
    private static final int NUM_OF_THREADS = 10;
    /**
     * Contains a list of all successful submitted job's responses.
     */
    private static final StringBuilder jobsStatus = new StringBuilder();
    /**
     * Contains a list of all failed submitted job's responses.
     */
    private static final StringBuilder jobsErrorStatus = new StringBuilder();
    /**
     * Contains a list of jobs to submit.
     */
    private static final List<CandidateJob> candidateJobs = new ArrayList<>();
    /**
     * Connection object needed for z/OSMF Rest API call.
     */
    private static ZosConnection connection;
    /**
     * Partition data set location where members are located to submit a job for each.
     */
    private static String pdsLocation;
    /**
     * User's account number to use for jcl job card for each job that will be submitted.
     */
    private static String accountNumber;
    /**
     * System ID to specify a particular system especially in a sysplex environment.
     * This field is optional.
     */
    private static String ssid;

    /**
     * Initial setup performs the readiness objects need for the automation.
     */
    private static void initialSetup() {
        final var hostName = System.getProperty("hostName");
        final var zosmfPort = System.getProperty("zosmfPort");
        final var userName = System.getProperty("userName");
        final var password = System.getProperty("password");
        pdsLocation = System.getProperty("pdsLocation");
        accountNumber = System.getProperty("accountNumber");
        ValidateUtils.checkNullParameter(hostName == null, "-DhostName not specified");
        ValidateUtils.checkNullParameter(zosmfPort == null, "-DzosmfPort not specified");
        ValidateUtils.checkNullParameter(userName == null, "-DuserName not specified");
        ValidateUtils.checkNullParameter(password == null, "-Dpassword not specified");
        ValidateUtils.checkNullParameter(pdsLocation == null, "-DpdsLocation not specified");
        ValidateUtils.checkNullParameter(accountNumber == null, "-accountNumber not specified");
        ssid = System.getProperty("ssid"); // optional no null check as such
        connection = new ZosConnection(hostName, zosmfPort, userName, password);
    }

    /**
     * Print out each submitted job failed status.
     */
    public static void jobLstFailureStatus() {
        if (!jobsErrorStatus.isEmpty()) {
            System.out.println("Following jobs failed: ");
            System.out.println(jobsErrorStatus);
        }
    }

    /**
     * Retrieve a list of member names from a data set location from pdsLocation parameter.
     */
    private static void jobLstSetup() {
        try {
            final var params = new ListParams.Builder().attribute(AttributeType.MEMBER).build();
            final var members = new DsnList(connection).getMembers(pdsLocation, params);
            for (Member member : members) {
                final var candidate = new CandidateJob(
                        pdsLocation,
                        member.getMember().orElseThrow(() -> new ZosmfRequestException("member missing")),
                        accountNumber,
                        ssid);
                candidateJobs.add(candidate);
            }
        } catch (ZosmfRequestException e) {
            final String errMsg = Util.getResponsePhrase(e.getResponse());
            throw new RuntimeException((errMsg != null ? errMsg : e.getMessage()));
        }
    }

    /**
     * Print out each submitted job success status.
     */
    public static void jobLstSuccessStatus() {
        if (!jobsStatus.isEmpty()) {
            System.out.println("Following jobs submitted successfully, status:");
            System.out.println(jobsStatus);
        }
    }

    /**
     * Take the list of members compiled and submit a job for each.
     */
    private static void submitJobs() {
        final var pool = Executors.newFixedThreadPool(NUM_OF_THREADS);
        final var futures = new ArrayList<Future<Response>>();

        candidateJobs.forEach(j -> futures.add(pool.submit(new FutureSubmit(j, connection))));

        futures.forEach(f -> {
            Response result;
            try {
                result = f.get(TIMEOUT, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                jobsErrorStatus.append(e.getMessage()).append("\n");
                return; // continue
            }
            if (!result.isSuccess()) {
                jobsErrorStatus.append(result.message()).append("\n");
            } else {
                jobsStatus.append(result.message()).append("\n");
            }
        });

        pool.shutdownNow();
    }

    /**
     * Main method that drives the automation.
     *
     * @param args no args used
     */
    public static void main(String[] args) {
        initialSetup();
        jobLstSetup();
        submitJobs();
        jobLstSuccessStatus();
        jobLstFailureStatus();
    }

}
