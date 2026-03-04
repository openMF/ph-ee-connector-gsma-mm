package org.mifos.connector.gsma.transfer;

import static org.mifos.connector.gsma.camel.config.CamelProperties.ERROR_INFORMATION;
import static org.mifos.connector.gsma.camel.config.CamelProperties.GSMA_QUOTE_FAILED;
import static org.mifos.connector.gsma.camel.config.CamelProperties.QUOTE_ID;
import static org.mifos.connector.gsma.camel.config.CamelProperties.QUOTE_REFERENCE;
import static org.mifos.connector.gsma.camel.config.CamelProperties.TRANSACTION_ID;
import static org.mifos.connector.gsma.zeebe.ZeebeMessages.GSMA_QUOTE_RESPONSE;

import io.camunda.zeebe.client.ZeebeClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class QuoteResponseProcessor implements Processor {

    @Autowired
    private ZeebeClient zeebeClient;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${zeebe.client.ttl}")
    private int timeToLive;

    @Override
    public void process(Exchange exchange) {

        Map<String, Object> variables = new HashMap<>();

        Object hasTransferFailed = exchange.getProperty(GSMA_QUOTE_FAILED);

        if (hasTransferFailed != null && (boolean) hasTransferFailed) {
            variables.put(GSMA_QUOTE_FAILED, true);
            variables.put(ERROR_INFORMATION, exchange.getIn().getBody(String.class));
        } else {
            variables.put(QUOTE_ID, exchange.getProperty(QUOTE_ID));
            variables.put(QUOTE_REFERENCE, exchange.getProperty(QUOTE_REFERENCE));
            variables.put(GSMA_QUOTE_FAILED, false);
        }

        logger.info("Publishing quote message variables: " + variables);

        zeebeClient.newPublishMessageCommand().messageName(GSMA_QUOTE_RESPONSE)
                .correlationKey(exchange.getProperty(TRANSACTION_ID, String.class)).timeToLive(Duration.ofMillis(timeToLive))
                .variables(variables).send().join();

    }

}
