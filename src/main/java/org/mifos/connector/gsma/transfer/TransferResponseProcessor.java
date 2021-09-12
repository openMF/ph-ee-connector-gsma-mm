package org.mifos.connector.gsma.transfer;

import io.camunda.zeebe.client.ZeebeClient;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.mifos.connector.gsma.camel.config.CamelProperties.ERROR_INFORMATION;
import static org.mifos.connector.gsma.camel.config.CamelProperties.TRANSACTION_ID;
import static org.mifos.connector.gsma.zeebe.ZeebeExpressionVariables.TRANSACTION_FAILED;
import static org.mifos.connector.gsma.zeebe.ZeebeExpressionVariables.TRANSFER_STATE;
import static org.mifos.connector.gsma.zeebe.ZeebeMessages.TRANSFER_MESSAGE;
import static org.mifos.connector.gsma.zeebe.ZeebeMessages.TRANSFER_RESPONSE;

@Component
public class TransferResponseProcessor implements Processor {

    @Autowired
    private ZeebeClient zeebeClient;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${zeebe.client.ttl}")
    private int timeToLive;

    @Override
    public void process(Exchange exchange) {

        Map<String, Object> variables = new HashMap<>();

        Object hasTransferFailed = exchange.getProperty(TRANSACTION_FAILED);

        if (hasTransferFailed != null && (boolean)hasTransferFailed) {
            variables.put(TRANSACTION_FAILED, true);
            variables.put(ERROR_INFORMATION, exchange.getIn().getBody(String.class));
        } else {
            variables.put(TRANSFER_STATE, "COMMITTED");
            variables.put(TRANSACTION_FAILED, false);

            zeebeClient.newPublishMessageCommand()
                    .messageName(TRANSFER_MESSAGE)
                    .correlationKey(exchange.getProperty(TRANSACTION_ID, String.class))
                    .variables(variables)
                    .send()
                    .join();
        }

        logger.info("Publishing transaction message variables: " + variables);

        zeebeClient.newPublishMessageCommand()
                .messageName(TRANSFER_RESPONSE)
                .correlationKey(exchange.getProperty(TRANSACTION_ID, String.class))
                .timeToLive(Duration.ofMillis(timeToLive))
                .variables(variables)
                .send()
                .join();

    }
}
