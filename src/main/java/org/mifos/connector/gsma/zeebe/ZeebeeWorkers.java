package org.mifos.connector.gsma.zeebe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import org.mifos.connector.common.channel.dto.TransactionChannelRequestDTO;
import org.mifos.connector.common.gsma.dto.GSMATransaction;
import org.mifos.connector.common.mojaloop.dto.QuoteSwitchRequestDTO;
import org.mifos.connector.common.mojaloop.type.AmountType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;

import static org.mifos.connector.gsma.zeebe.ZeebeVariables.QUOTE_SWITCH_RESULT;

@Component
public class ZeebeeWorkers {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ZeebeClient zeebeClient;

    @Autowired
    private ZeebeProcessStarter zeebeProcessStarter;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${zeebe.client.evenly-allocated-max-jobs}")
    private int workerMaxJobs;

    @PostConstruct
    public void setupWorkers() {

        zeebeClient.newWorker()
                .jobType("payeeProcess")
                .handler((client, job) -> {
                    logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());

                    Map<String, Object> variables = job.getVariablesAsMap();
                    TransactionChannelRequestDTO channelRequest = objectMapper.readValue((String) variables.get("channelRequest"), TransactionChannelRequestDTO.class);

                    QuoteSwitchRequestDTO quoteRequest = new QuoteSwitchRequestDTO();
                    quoteRequest.setAmount(channelRequest.getAmount());
                    quoteRequest.setPayee(channelRequest.getPayee());
                    quoteRequest.setPayer(channelRequest.getPayer());
                    quoteRequest.setAmountType(AmountType.SEND);

                    variables.put(QUOTE_SWITCH_RESULT, quoteRequest);
                    zeebeProcessStarter.startZeebeWorkflow("gsma_payee_process", variables);

                    client.newCompleteCommand(job.getKey())
                            .send()
                            .join();
                })
                .name("payeeProcess")
                .maxJobsActive(workerMaxJobs)
                .open();


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
