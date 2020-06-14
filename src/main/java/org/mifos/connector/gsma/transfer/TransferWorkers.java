package org.mifos.connector.gsma.transfer;

import io.zeebe.client.ZeebeClient;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;

import static org.mifos.connector.gsma.camel.config.CamelProperties.CORELATION_ID;
import static org.mifos.connector.gsma.camel.config.CamelProperties.TRANSACTION_BODY;
import static org.mifos.connector.gsma.zeebe.ZeebeExpressionVariables.TRANSFER_RETRY_COUNT;

@Component
public class TransferWorkers {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ZeebeClient zeebeClient;

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private CamelContext camelContext;

    @Value("${zeebe.client.evenly-allocated-max-jobs}")
    private int workerMaxJobs;

    @PostConstruct
    public void setupWorkers() {

        zeebeClient.newWorker()
                .jobType("initiateTransfer")
                .handler((client, job) -> {
                    logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
                    Map<String, Object> variables = job.getVariablesAsMap();
                    variables.put(TRANSFER_RETRY_COUNT, 1 + (Integer) variables.getOrDefault(TRANSFER_RETRY_COUNT, -1));

                    Exchange exchange = new DefaultExchange(camelContext);
                    exchange.setProperty(CORELATION_ID, variables.get("transactionId"));
                    exchange.setProperty(TRANSACTION_BODY, variables.get("channelBody"));

                    producerTemplate.send("direct:transfer-route", exchange);

                    client.newCompleteCommand(job.getKey())
                            .variables(variables)
                            .send()
                            .join();
                })
                .name("initiateTransfer")
                .maxJobsActive(workerMaxJobs)
                .open();
    }
}
