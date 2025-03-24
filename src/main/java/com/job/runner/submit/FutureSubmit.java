package com.job.runner.submit;

import com.job.runner.record.CandidateJob;
import com.job.runner.record.Response;
import zowe.client.sdk.core.ZosConnection;

import java.util.concurrent.Callable;

/**
 * Class wraps a Callable object to be used to perform a threaded task that submits a job.
 *
 * @author Frank Giordano
 */
public class FutureSubmit extends Submit implements Callable<Response> {

    /**
     * FutureSubmit constructor
     *
     * @param candidateJob job to be submitted
     * @param connection   connection info for z/OSMF
     */
    public FutureSubmit(CandidateJob candidateJob, ZosConnection connection) {
        super(candidateJob, connection);
    }

    /**
     * Perform the job submit action.
     *
     * @return job response
     */
    @Override
    public Response call() {
        return this.submitJob();
    }

}