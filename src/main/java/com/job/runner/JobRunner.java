package com.job.runner;

import com.job.runner.record.CandidateJob;
import com.job.runner.record.Response;
import com.job.runner.submit.FutureSubmit;
import zowe.client.sdk.core.ZOSConnection;
import zowe.client.sdk.zosfiles.ZosDsnList;
import zowe.client.sdk.zosfiles.input.ListParams;
import zowe.client.sdk.zosfiles.types.AttributeType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Following program provides a way to define a set of jobs to be submitted together in like a multi batch process on
 * your z/OS instance(s). It can be used to automate a set of jobs to be submitted on a scheduled bases using for
 * instance Jenkins. Each job is submitted in an asynchronous way. As such, these jobs are submitted via a thread pool.
 * The advantage of a thread pool speeds up execution and completion time.
 *
 * @author Frank Giordano
 */
public class JobRunner {

    private static final int TIMEOUT = 300;
    private static final int NUM_OF_THREADS = 10;
    private static final StringBuilder jobsStatus = new StringBuilder();
    private static final StringBuilder jobsErrorStatus = new StringBuilder();
    private static final List<CandidateJob> candidateJobs = new ArrayList<>();
    private static ZOSConnection connection;
    private static String pdsLocation;
    private static String accountNumber;
    private static String ssid;

    private static void initialSetup() {
        final var hostName = System.getProperty("hostName");
        final var zosmfPort = System.getProperty("zosmfPort");
        final var userName = System.getProperty("userName");
        final var password = System.getProperty("password");
        pdsLocation = System.getProperty("pdsLocation");
        accountNumber = System.getProperty("accountNumber");
        try {
            ssid = System.getProperty("ssid");
        } catch (Exception e) {
            ssid = null;
        }
        connection = new ZOSConnection(hostName, zosmfPort, userName, password);
    }

    public static void jobLstFailureStatus() {
        if (!jobsErrorStatus.isEmpty()) {
            System.out.println("Following jobs failed: ");
            System.out.println(jobsErrorStatus);
        }
    }

    private static void jobLstSetup() throws Exception {
        final var params = new ListParams.Builder().attribute(AttributeType.MEMBER).build();
        final var members = new ZosDsnList(connection).listDsnMembers(pdsLocation, params);
        members.forEach(m -> candidateJobs.add(new CandidateJob(pdsLocation, m.getMember().get(), accountNumber, ssid)));
    }

    public static void jobLstSuccessStatus() {
        if (!jobsStatus.isEmpty()) {
            System.out.println("Following jobs submitted successfully, status:");
            System.out.println(jobsStatus);
        }
    }

    private static void submitJobs() {
        final var pool = Executors.newFixedThreadPool(NUM_OF_THREADS);
        final var futures = new ArrayList<Future<Response>>();

        candidateJobs.forEach(j -> futures.add(pool.submit(new FutureSubmit(j, connection))));

        futures.forEach(f -> {
            Response result;
            try {
                result = f.get(TIMEOUT, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                e.printStackTrace();
                jobsErrorStatus.append(e.getMessage());
                return; // continue
            }
            if (!result.isSuccess()) {
                jobsErrorStatus.append(result.message());
            } else {
                jobsStatus.append(result.message());
            }
        });

        pool.shutdownNow();
    }

    public static void main(String[] args) throws Exception {
        initialSetup();
        jobLstSetup();
        submitJobs();
        jobLstSuccessStatus();
        jobLstFailureStatus();
    }

}
