package org.mifos.connector.gsma.response;

import io.camunda.zeebe.client.ZeebeClient;
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
                    exchange.setProperty(CORRELATION_ID, variables.get("transactionId"));
                    exchange.setProperty(RESPONSE_OBJECT_TYPE, "transaction");

                    producerTemplate.send("direct:response-route", exchange);

                    variables.put(TRANSACTION_OBJECT_AVAILABLE, exchange.getProperty(RESPONSE_OBJECT_AVAILABLE, Boolean.class));
                    if (exchange.getProperty(RESPONSE_OBJECT_AVAILABLE, Boolean.class))
                        variables.put(TRANSACTION_OBJECT, exchange.getProperty(RESPONSE_OBJECT, String.class));

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
