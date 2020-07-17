package org.mifos.connector.gsma.zeebe;

import io.zeebe.client.ZeebeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class ZeebeeWorkers {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ZeebeClient zeebeClient;

    @Value("${zeebe.client.evenly-allocated-max-jobs}")
    private int workerMaxJobs;

    @PostConstruct
    public void setupWorkers() {

        zeebeClient.newWorker()
                .jobType("testerWorker")
                .handler((client, job) -> {
                    logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
                    client.newCompleteCommand(job.getKey())
                            .send()
                            .join();
                })
                .name("testerWorker")
                .maxJobsActive(workerMaxJobs)
                .open();


        zeebeClient.newWorker()
                .jobType("sendTimeoutChannel")
                .handler((client, job) -> {
                    logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
                    logger.info("TRANSACTION TIMEOUT");
                    client.newCompleteCommand(job.getKey())
                            .send()
                            .join();
                })
                .name("sendTimeoutChannel")
                .maxJobsActive(workerMaxJobs)
                .open();


        zeebeClient.newWorker()
                .jobType("sendFailureChannel")
                .handler((client, job) -> {
                    logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
                    logger.info("TRANSACTION FAILED");
                    client.newCompleteCommand(job.getKey())
                            .send()
                            .join();
                })
                .name("sendFailureChannel")
                .maxJobsActive(workerMaxJobs)
                .open();


        zeebeClient.newWorker()
                .jobType("sendConfirmationChannel")
                .handler((client, job) -> {
                    logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
                    logger.info("TRANSACTION WAS SUCCESSFUL.");
                    client.newCompleteCommand(job.getKey())
                            .send()
                            .join();
                })
                .name("sendConfirmationChannel")
                .maxJobsActive(workerMaxJobs)
                .open();

    }
}
