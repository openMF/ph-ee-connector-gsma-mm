package org.mifos.connector.gsma.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.ZeebeClient;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultExchange;
import org.mifos.connector.common.gsma.dto.GSMATransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;

import static org.mifos.connector.gsma.camel.config.CamelProperties.*;
import static org.mifos.connector.gsma.zeebe.ZeebeExpressionVariables.TRANSFER_RETRY_COUNT;

@Component
public class TransactionStateWorker {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ObjectMapper objectMapper;
    
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
                .jobType("transactionState")
                .handler((client, job) -> {
                    logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
                    Map<String, Object> variables = job.getVariablesAsMap();
                    variables.put(TRANSFER_RETRY_COUNT, 1 + (Integer) variables.getOrDefault(TRANSFER_RETRY_COUNT, 0));
                    GSMATransaction gsmaChannelRequest = objectMapper.readValue((String) variables.get("gsmaChannelRequest"), GSMATransaction.class);

                    Exchange exchange = new DefaultExchange(camelContext);
                    exchange.setProperty(CORRELATION_ID, variables.get("transactionId"));
                    exchange.setProperty(TRANSACTION_ID, variables.get("transactionId"));
                    exchange.setProperty(RECEIVING_TENANT, gsmaChannelRequest.getReceivingLei());

                    producerTemplate.send("direct:transaction-state-check", exchange);

                    variables.put(STATUS_AVAILABLE, exchange.getProperty(STATUS_AVAILABLE, Boolean.class));
                    if (exchange.getProperty(STATUS_AVAILABLE, Boolean.class)) {
                        variables.put(TRANSACTION_STATUS, exchange.getProperty(TRANSACTION_STATUS, String.class));
                    }

                    client.newCompleteCommand(job.getKey())
                            .variables(variables)
                            .send()
                            .join();
                })
                .name("transactionState")
                .maxJobsActive(workerMaxJobs)
                .open();

    }
}
