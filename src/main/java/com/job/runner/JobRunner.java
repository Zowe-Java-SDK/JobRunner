package com.job.runner;

import com.job.runner.record.CandidateJob;
import com.job.runner.record.Response;
import com.job.runner.submit.FutureSubmit;
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
 * The following program provides a way to define a set of jobs to be submitted together on your z/OS instance.
 * It can be used to automate a set of jobs to be submitted on a scheduled basis using, for instance, Jenkins.
 * Each job is submitted in an asynchronous manner, speeding up execution and completion time.
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
    private static List<CandidateJob> candidateJobs = new ArrayList<>();
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
        var hostName = System.getProperty("hostName");
        var zosmfPort = System.getProperty("zosmfPort");
        var userName = System.getProperty("userName");
        var password = System.getProperty("password");
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
     * Print all submitted job statuses.
     */
    private static void jobLstStatus() {
        if (!jobsStatus.isEmpty()) {
            System.out.println("Following jobs successfully executed:");
            System.out.println(jobsStatus);
        }
        if (!jobsErrorStatus.isEmpty()) {
            System.out.println("Following jobs failed to execute:");
            System.out.println(jobsErrorStatus);
        }
    }

    /**
     * Retrieve a list of member names from partition dataset location from pdsLocation parameter.
     */
    private static void jobLstSetup() {
        var params = new ListParams.Builder().attribute(AttributeType.MEMBER).build();
        List<Member> members;
        try {
            members = new DsnList(connection).getMembers(pdsLocation, params);
        } catch (ZosmfRequestException e) {
            throw new RuntimeException(e);
        }
        candidateJobs = members.stream()
                .filter(m -> m.getMember().isPresent())
                .map(m -> makeCandidateJob(m.getMember().get()))
                .toList();
    }

    /**
     * Helper method to create CandidateJob object.
     *
     * @param name string representing a member name
     * @return CandidateJob object
     */
    private static CandidateJob makeCandidateJob(String name) {
        return new CandidateJob(pdsLocation, name, accountNumber, ssid);
    }

    /**
     * Take the list of members and submit a z/OS job for each and retrieve each job outcome status.
     */
    private static void submitJobs() {
        var pool = Executors.newFixedThreadPool(NUM_OF_THREADS);
        var futures = new ArrayList<Future<Response>>();
        var responses = new ArrayList<Response>();

        candidateJobs.forEach(j -> futures.add(pool.submit(new FutureSubmit(j, connection))));
        futures.forEach(f -> {
            try {
                responses.add(f.get(TIMEOUT, TimeUnit.SECONDS));
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                jobsErrorStatus.append(e.getMessage());
            }
        });
        responses.stream().filter(Response::isSuccess).forEach(jobsStatus::append);
        responses.stream().filter(Response::isFailed).forEach(jobsErrorStatus::append);

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
        jobLstStatus();
    }

}
