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

public class Submit {

    private static final Logger LOG = LogManager.getLogger(Submit.class);
    private final String jobIdentifier;
    private final CandidateJob candidateJob;
    private final ZosDsnDownload zosDsnDownload;
    private final DownloadParams downloadParams;
    private final SubmitJobParams submitParams;
    private final SubmitJobs submitJob;
    private final MonitorJobs monitorJobs;
    private String jclContent = null;

    public Submit(CandidateJob candidateJob, ZOSConnection connection) {
        this.candidateJob = candidateJob;
        this.zosDsnDownload = new ZosDsnDownload(connection);
        this.submitJob = new SubmitJobs(connection);
        this.monitorJobs = new MonitorJobs(connection);
        this.submitParams = new SubmitJobParams(candidateJob.dataset() + "(" + candidateJob.member() + ")");
        this.jobIdentifier = submitParams.getJobDataSet().get();
        this.downloadParams = new DownloadParams.Builder().build();
    }

    private String getMessage(Exception e) {
        return jobIdentifier + " - " + e.getMessage();
    }

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

    public Response submitJob() {
        return submit();
    }

}
