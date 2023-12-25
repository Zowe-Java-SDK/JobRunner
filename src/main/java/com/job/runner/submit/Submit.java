package com.job.runner.submit;

import com.job.runner.record.CandidateJob;
import com.job.runner.record.Response;
import com.job.runner.utility.Util;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import zowe.client.sdk.core.ZosConnection;
import zowe.client.sdk.rest.exception.ZosmfRequestException;
import zowe.client.sdk.zosfiles.dsn.input.DownloadParams;
import zowe.client.sdk.zosfiles.dsn.methods.DsnGet;
import zowe.client.sdk.zosjobs.methods.JobMonitor;
import zowe.client.sdk.zosjobs.methods.JobSubmit;
import zowe.client.sdk.zosjobs.response.Job;
import zowe.client.sdk.zosjobs.types.JobStatus;

import java.io.IOException;
import java.io.StringWriter;

/**
 * Class of job submission. Contains the methods to perform a job submit.
 *
 * @author Frank Giordano
 */
public class Submit {

    private static final Logger LOG = LogManager.getLogger(Submit.class);
    /**
     * Job identifier in the following format i.e. xxx.xxx.xxx(xxx)
     */
    private final String jobIdentifier;
    /**
     * Job info that will be used to submit the job.
     */
    private final CandidateJob candidateJob;
    /**
     * Download object to be used to download a member that represent a job.
     */
    private final DsnGet dsnGet;
    /**
     * SubmitJob object to perform the submit job action.
     */
    private final JobSubmit jobSubmit;
    /**
     * MonitorJobs object to be used to monitor a job until ended.
     */
    private final JobMonitor jobMonitor;
    /**
     * JCL content of the member that represent a job to be submitted.
     */
    private String jclContent = null;

    /**
     * Submit constructor.
     *
     * @param candidateJob job to be submitted
     * @param connection   connection info for z/OSMF
     */
    public Submit(CandidateJob candidateJob, ZosConnection connection) {
        this.candidateJob = candidateJob;
        this.dsnGet = new DsnGet(connection);
        this.jobSubmit = new JobSubmit(connection);
        this.jobMonitor = new JobMonitor(connection);
        this.jobIdentifier = String.format("%s(%s)", candidateJob.dataset(), candidateJob.member());
    }

    /**
     * Formulate a job failure message to be reported as a status.
     *
     * @param msg string message value
     * @return string message
     */
    private String getMessage(String msg) {
        return jobIdentifier + " - " + msg;
    }

    /**
     * Read member JCL content and append generated job card to content to a global variable to be used in a subsequent
     * method to submit it as a job.
     *
     * @return Response object
     */
    private Response setupJcl() {
        final var MAX_TRIES = 5;
        final var downloadParams = new DownloadParams.Builder().build();
        final var ssid = candidateJob.ssid() != null ? "/*JOBPARM SYSAFF=" + candidateJob.ssid() : "//*";
        final var jobCard = """
                //%s JOB (%s),'%s',NOTIFY=&SYSUID,CLASS=A,
                //  MSGCLASS=X
                 %s
                """
                .formatted(candidateJob.member(), candidateJob.acctNum(), candidateJob.member(), ssid);
        var count = 0;
        while (true) {
            try {
                try (final var inputStream = dsnGet.get(this.jobIdentifier, downloadParams)) {
                    if (inputStream != null) {
                        StringWriter writer = new StringWriter();
                        IOUtils.copy(inputStream, writer, "UTF8");
                        jclContent = writer.toString();
                    }
                }

                if (jclContent.isBlank()) {
                    throw new IllegalStateException("Cannot retrieve JCL content");
                }
                jclContent = jobCard + jclContent;
                break;
            } catch (ZosmfRequestException | IOException e) {
                if (MAX_TRIES == ++count) {
                    if (e instanceof ZosmfRequestException zosmfRequestException) {
                        final String errMsg = Util.getResponsePhrase(zosmfRequestException.getResponse());
                        return new Response(getMessage((errMsg != null ? errMsg : e.getMessage())), false);
                    }
                    return new Response(getMessage(e.getMessage()), false);
                }
            }
        }
        return new Response(getMessage("no jcl content error"), false);
    }

    /**
     * Perform a job submit action.
     *
     * @return Response object
     */
    private Response submit() {
        final var response = setupJcl();
        if (!response.isSuccess()) {
            return response;
        }

        Job job;
        String returnCode;
        String jobId;
        try {
            job = jobSubmit.submitByJcl(jclContent, null, null);
            final var jobName = job.getJobName().orElseThrow(() -> new ZosmfRequestException("job name missing"));
            jobId = job.getJobId().orElseThrow(() -> new ZosmfRequestException("job id missing"));
            final var msg = "Waiting for jobName {} with jobId {} to complete.";
            LOG.info(msg, jobName, jobId);
            job = jobMonitor.waitByStatus(job, JobStatus.Type.OUTPUT);
            returnCode = job.getRetCode().orElseThrow(() -> new ZosmfRequestException("job return code missing"));
        } catch (ZosmfRequestException e) {
            final String errMsg = Util.getResponsePhrase(e.getResponse());
            return new Response(getMessage(errMsg != null ? errMsg : e.getMessage()), false);
        }

        if (!returnCode.startsWith("CC")) {
            try {
                Integer.parseInt(returnCode);
            } catch (NumberFormatException e) {
                return new Response(getMessage("invalid job return code " + returnCode), false);
            }
        }
        final var end = candidateJob.ssid() != null ? " with SSID=" + candidateJob.ssid() + "." : ".";
        final var msg = """
                Return code for %s and %s is %s%s
                """.formatted(jobIdentifier, jobId, returnCode, end);
        return new Response(msg, true);
    }

    /**
     * Wrapper method that calls submit() method.
     *
     * @return Response object
     */
    public Response submitJob() {
        return submit();
    }

}
