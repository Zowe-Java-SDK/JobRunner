package com.job.runner.submit;

import com.job.runner.record.CandidateJob;
import com.job.runner.record.Response;
import zowe.client.sdk.core.ZOSConnection;

import java.util.concurrent.Callable;

/**
 * Class wraps a Callable object to be used to perform a threaded task that submits a job.
 *
 * @author Frank Giordano
 */
public class FutureSubmit extends Submit implements Callable<Response> {

    public FutureSubmit(CandidateJob candidateJob, ZOSConnection connection) {
        super(candidateJob, connection);
    }

    @Override
    public Response call() {
        return this.submitJob();
    }

}