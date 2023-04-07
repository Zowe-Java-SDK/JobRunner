package com.job.runner.submit;

import com.job.runner.record.CandidateJob;
import com.job.runner.record.Response;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import zowe.client.sdk.core.ZOSConnection;
import zowe.client.sdk.zosfiles.ZosDsnDownload;
import zowe.client.sdk.zosfiles.input.DownloadParams;
import zowe.client.sdk.zosjobs.MonitorJobs;
import zowe.client.sdk.zosjobs.SubmitJobs;
import zowe.client.sdk.zosjobs.input.SubmitJobParams;
import zowe.client.sdk.zosjobs.response.Job;
import zowe.client.sdk.zosjobs.types.JobStatus;

import java.io.InputStream;
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
    private final ZosDsnDownload zosDsnDownload;
    /**
     * Download parameters.
     */
    private final DownloadParams downloadParams;
    /**
     * SubmitJob object to perform the submit job action.
     */
    private final SubmitJobs submitJob;
    /**
     * MonitorJobs object to be used to monitor a job until ended.
     */
    private final MonitorJobs monitorJobs;
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
    public Submit(CandidateJob candidateJob, ZOSConnection connection) {
        this.candidateJob = candidateJob;
        this.zosDsnDownload = new ZosDsnDownload(connection);
        this.submitJob = new SubmitJobs(connection);
        this.monitorJobs = new MonitorJobs(connection);
        final var submitParams = new SubmitJobParams(candidateJob.dataset() + "(" + candidateJob.member() + ")");
        this.jobIdentifier = submitParams.getJobDataSet().get();
        this.downloadParams = new DownloadParams.Builder().build();
    }

    /**
     * Formulate a job failure message to be reported as a status.
     *
     * @param e execution information
     * @return string message
     */
    private String getMessage(Exception e) {
        return jobIdentifier + " - " + e.getMessage();
    }

    /**
     * Read member JCL content and append generated job card to content to a global variable to be used in a subsequent
     * method to submit it as a job.
     *
     * @return response object when a failure occurs
     */
    private Response setupJcl() {
        var count = 0;
        final var MAX_TRIES = 3;
        while (true) {
            final var jobCard = """
                    //%s JOB (%s),'%s',NOTIFY=&SYSUID,CLASS=A,
                    //  MSGCLASS=X
                    %s
                    """.formatted(candidateJob.member(), candidateJob.acctNum(), candidateJob.member(),
                    candidateJob.ssid() != null ? "/*JOBPARM SYSAFF=" + candidateJob.ssid() : "//*");
            try {
                try (InputStream inputStream = zosDsnDownload.downloadDsn(
                        String.format("%s(%s)", candidateJob.dataset(), candidateJob.member()), downloadParams)) {
                    if (inputStream != null) {
                        StringWriter writer = new StringWriter();
                        IOUtils.copy(inputStream, writer, "UTF8");
                        jclContent = writer.toString();
                    }
                }
                if (jclContent.isEmpty()) {
                    throw new Exception("Cannot retrieve JCL content");
                }
                jclContent = jobCard + jclContent;
                break;
            } catch (Exception e) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
                if (++count == MAX_TRIES) {
                    return new Response(getMessage(e), false);
                }
            }
        }
        return null;
    }

    /**
     * Perform a job submit action.
     *
     * @return job response
     */
    private Response submit() {
        final var response = setupJcl();
        if (response != null) {
            return response;
        }

        Job job;
        try {
            job = submitJob.submitJcl(jclContent, null, null);
        } catch (Exception e) {
            return new Response(getMessage(e), false);
        }

        LOG.info("Waiting for jobName {} with jobId {} to complete.",
                job.getJobName().orElse("n\\a"), job.getJobId().orElse("n\\a"));

        try {
            job = monitorJobs.waitForJobStatus(job, JobStatus.Type.OUTPUT);
        } catch (Exception e) {
            return new Response(getMessage(e), false);
        }

        final var rc = job.getRetCode().orElse("n\\a");
        try {
            if (!rc.startsWith("CC")) {
                Integer.parseInt(rc);
            }
        } catch (NumberFormatException e) {
            return new Response(getMessage(new Exception("invalid job return code " + rc)), false);
        }

        final var message = """
                Return code for %s and %s is %s%s
                """.formatted(jobIdentifier, job.getJobId().orElse("n\\a"), rc,
                candidateJob.ssid() != null ? "with SSID=" + candidateJob.ssid() + "." : ".");
        return new Response(message, true);
    }

    /**
     * Wrapper method that calls submit() method.
     *
     * @return job response
     */
    public Response submitJob() {
        return submit();
    }

}
