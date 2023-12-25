# JobRunner  

Following project provides a way to define a set of jobs to be submitted together in like a multi batch process on your z/OS instance.

It can be used to automate a set of jobs to be submitted on a scheduled basis using for instance Jenkins.

Each job is submitted in an asynchronous way via a thread pool. Threads speeds up completion time and exceptions are easily isolated from each other.  
    
## Requirements
  
    Java 17  
    Maven
    z/OSMF installed on your backend z/OS instance  
   
## Workflow 
    
The project requires the following attributes to be sent as parameter values to the maven program execution command:
  
    hostname
    zosmfPort
    userName
    password
    accountNumber
    pdsLocation 
    ssid (optional) 
        
The following attributes are needed for connection to execute z/OSMF Rest API calls:  
    
    hostname
    zosmfPort
    userName
    password    
    
The following attributes are needed to create a job card detail for each job that gets appended to the JCL content:     
    
    accountLocation
    ssid (optional)     
  
The following attribute defines the Partition Dataset (PDS) location where each member in the PDS is submitted by the program:
  
    pdsLocation
        
Each member in pdsLocation needs to exist with its job card info stripped. The program will read each member's content and append a job card and submit it.
     
The job card used for each job submission is generated in the following way:   
  
            final var jobCard = """
                //%s JOB (%s),'%s',NOTIFY=&SYSUID,CLASS=A,
                //  MSGCLASS=X
                 %s
                """
                .formatted(candidateJob.member(), candidateJob.acctNum(), candidateJob.member(), ssid);
  
NOTE: The processing of the automation of this program is done via the Zowe Java Client SDK. The SDK performs z/OSMF REST API calls against the backend z/OS instance.  
  
## Build And Execute
  
At the root directory prompt, execute the following maven command:
  
    mvn clean compile exec:java 
    -Dexec.mainClass=com.job.runner.JobRunner 
    -DhostName=xxxxxxx.xxxx.xxxx.net 
    -DzosmfPort=xxx 
    -DuserName=xxx 
    -Dpassword=xxx
    -DaccountNumber=xxx 
    -DpdsLocation=xxx.xxx.xxx.xxx
    -Dssid=xxx
  
change the -D values accordingly for your environment.  
  
-Dssid is an optional parameter.     
  
## Example Execution Output  
  
Let's say for instance the following PDS location is available with members that are stripped of their job card info:  

    > cd CCSGLBL.PUBLIC.CCSTEAM.JCL
    set to CCSGLBL.PUBLIC.CCSTEAM.JCL
    > ls -l
    user    cdate      mdate      mod  member
    BAAUTO  2023/03/06 2023/04/06 99   CCSDVTST
    BAAUTO  2021/02/01 2023/04/06 21   COMRSJCL
    BAAUTO  2020/06/09 2023/04/06 99   FRSREFR
    BAAUTO  2020/06/09 2023/04/06 99   LMPREFR
    BAAUTO  2016/01/29 2023/04/06 99   MTSMP150
  
and the username BAAUTO will be used to execute the job submission with its account number 105300000.  
  
Execute the program with the info noted above, each member will be submitted as a job:  

    mvn clean compile exec:java
    -Dexec.mainClass=com.job.runner.JobRunner
    -DhostName=usilfake.broadcast.net
    -DzosmfPort=1443 
    -DuserName=BAAUTO
    -Dpassword=QABR
    -Dssid=BA31
    -DaccountNumber=105300000
    -DpdsLocation=CCSGLBL.PUBLIC.CCSTEAM.JCL
    [INFO] Scanning for projects...
    [INFO]
    [INFO] ----------------------< com.job.runner:JobRunner >----------------------
    [INFO] Building JobRunner 1.0-SNAPSHOT
    [INFO] --------------------------------[ jar ]---------------------------------
    [INFO]
    [INFO] --- maven-clean-plugin:2.5:clean (default-clean) @ JobRunner ---
    [INFO] Deleting C:\Users\fg892105\IdeaProjects\JobRunnerHandler\target
    [INFO]
    [INFO] --- maven-resources-plugin:2.6:resources (default-resources) @ JobRunner ---
    [INFO] Using 'UTF-8' encoding to copy filtered resources.
    [INFO] Copying 1 resource
    [INFO]
    [INFO] --- maven-compiler-plugin:3.1:compile (default-compile) @ JobRunner ---
    [INFO] Changes detected - recompiling the module!
    [INFO] Compiling 5 source files to C:\Users\fg892105\IdeaProjects\JobRunnerHandler\target\classes
    [INFO]
    [INFO] --- exec-maven-plugin:3.1.0:java (default-cli) @ JobRunner ---
    19:56:18.306 INFO  - Waiting for jobName FRSREFR with jobId JOB35751 to complete.
    19:56:18.308 INFO  - Waiting for status "OUTPUT"
    19:56:18.895 INFO  - Waiting for jobName MTSMP150 with jobId JOB35753 to complete.
    19:56:18.896 INFO  - Waiting for status "OUTPUT"
    19:56:18.907 INFO  - Waiting for jobName COMRSJCL with jobId JOB35752 to complete.
    19:56:18.907 INFO  - Waiting for status "OUTPUT"
    19:56:18.951 INFO  - Waiting for jobName LMPREFR with jobId JOB35754 to complete.
    19:56:18.952 INFO  - Waiting for status "OUTPUT"
    19:56:18.990 INFO  - Waiting for jobName CCSDVTST with jobId JOB35756 to complete.
    19:56:18.991 INFO  - Waiting for status "OUTPUT"
    19:57:02.351 INFO  - Waiting for status "OUTPUT"
    19:57:10.107 INFO  - Waiting for status "OUTPUT"
    Following jobs submitted successfully, status:
    Return code for CCSGLBL.PUBLIC.CCSTEAM.JCL(CCSDVTST) and JOB35756 is CC 0000 with SSID=BA31.
    Return code for CCSGLBL.PUBLIC.CCSTEAM.JCL(COMRSJCL) and JOB35752 is CC 0000 with SSID=BA31.
    Return code for CCSGLBL.PUBLIC.CCSTEAM.JCL(FRSREFR) and JOB35751 is CC 0001 with SSID=BA31.
    Return code for CCSGLBL.PUBLIC.CCSTEAM.JCL(LMPREFR) and JOB35754 is CC 0000 with SSID=BA31.
    Return code for CCSGLBL.PUBLIC.CCSTEAM.JCL(MTSMP150) and JOB35753 is CC 0012 with SSID=BA31.
    
    [INFO] ------------------------------------------------------------------------
    [INFO] BUILD SUCCESS
    [INFO] ------------------------------------------------------------------------
    [INFO] Total time:  01:00 min
    [INFO] Finished at: 2023-04-06T19:57:11-04:00
    [INFO] ------------------------------------------------------------------------

    Process finished with exit code 0   
  
## Logger  
  
log4j2 is configured for the project.  
   
Find the log4j2.xml file under the resources directory. By default, log level is set to INFO.  
  
For debugging output especially for Zowe Client Java SDK, set the log level to debug.  
  
