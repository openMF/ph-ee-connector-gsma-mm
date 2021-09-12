package org.mifos.connector.gsma.account;

import io.camunda.zeebe.client.ZeebeClient;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.mifos.connector.gsma.transfer.CorrelationIDStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.mifos.connector.gsma.camel.config.CamelProperties.*;
import static org.mifos.connector.gsma.zeebe.ZeebeExpressionVariables.LINK_CREATE_FAILED;
import static org.mifos.connector.gsma.zeebe.ZeebeMessages.LINK_RESPONSE;

@Component
public class LinksResponseProcessor implements Processor {

    @Autowired
    private ZeebeClient zeebeClient;

    @Autowired
    private CorrelationIDStore correlationIDStore;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${zeebe.client.ttl}")
    private int timeToLive;

    @Override
    public void process(Exchange exchange) throws Exception {

        Map<String, Object> variables = new HashMap<>();

        Object didLinkCreateFail = exchange.getProperty(LINK_CREATE_FAILED);
        if (didLinkCreateFail != null && (boolean) didLinkCreateFail) {
            variables.put(ERROR_INFORMATION, exchange.getProperty(ERROR_INFORMATION, String.class));
            variables.put(LINK_CREATE_FAILED, true);
        } else {
            variables.put(LINK_REFERENCE, exchange.getProperty(LINK_REFERENCE, String.class));
            variables.put(LINK_CREATE_FAILED, false);
        }

        String transactionID = correlationIDStore.getClientCorrelation(exchange.getProperty(CORRELATION_ID, String.class));

        logger.info("Publishing link response message variables: " + variables);

        zeebeClient.newPublishMessageCommand()
                .messageName(LINK_RESPONSE)
                .correlationKey(transactionID)
                .timeToLive(Duration.ofMillis(timeToLive))
                .variables(variables)
                .send()
                .join();
    }
}
