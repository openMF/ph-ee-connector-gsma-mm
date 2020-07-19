package org.mifos.connector.gsma.response;

import io.zeebe.client.ZeebeClient;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.PostConstruct;
import java.util.Map;

import static org.mifos.connector.gsma.camel.config.CamelProperties.*;
import static org.mifos.connector.gsma.camel.config.CamelProperties.TRANSACTION_STATUS;

public class TransactionResponseWorker {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ZeebeClient zeebeClient;

    @Value("${zeebe.client.evenly-allocated-max-jobs}")
    private int workerMaxJobs;

    @PostConstruct
    public void setupWorkers() {

        zeebeClient.newWorker()
                .jobType("transactionResponse")
                .handler((client, job) -> {
                    logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
                    Map<String, Object> variables = job.getVariablesAsMap();

                    Exchange exchange = new DefaultExchange(camelContext);
                    exchange.setProperty(CORELATION_ID, variables.get("transactionId"));

                    producerTemplate.send("direct:transaction-response", exchange);

                    variables.put(TRANSACTION_OBJECT_AVAILABLE, exchange.getProperty(TRANSACTION_OBJECT_AVAILABLE, Boolean.class));
                    if (exchange.getProperty(TRANSACTION_OBJECT_AVAILABLE, Boolean.class))
                        variables.put(TRANSACTION_OBJECT, exchange.getProperty(TRANSACTION_OBJECT, String.class));

                    client.newCompleteCommand(job.getKey())
                            .variables(variables)
                            .send()
                            .join();
                })
                .name("transactionResponse")
                .maxJobsActive(workerMaxJobs)
                .open();

    }

}
