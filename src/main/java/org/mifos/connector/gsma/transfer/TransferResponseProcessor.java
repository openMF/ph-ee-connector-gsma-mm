package org.mifos.connector.gsma.transfer;

import io.zeebe.client.ZeebeClient;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.mifos.connector.gsma.camel.config.CamelProperties.ERROR_INFORMATION;
import static org.mifos.connector.gsma.camel.config.CamelProperties.TRANSACTION_ID;
import static org.mifos.connector.gsma.zeebe.ZeebeExpressionVariables.TRANSACTION_FAILED;
import static org.mifos.connector.gsma.zeebe.ZeebeExpressionVariables.TRANSFER_STATE;
import static org.mifos.connector.gsma.zeebe.ZeebeMessages.TRANSFER_RESPONSE;

@Component
public class TransferResponseProcessor implements Processor {

    @Autowired
    private ZeebeClient zeebeClient;

    @Override
    public void process(Exchange exchange) {

        Map<String, Object> variables = new HashMap<>();

        Object hasTransferFailed = exchange.getProperty(TRANSACTION_FAILED);

        if (hasTransferFailed != null && (boolean)hasTransferFailed) {
            variables.put(TRANSACTION_FAILED, true);
            variables.put(ERROR_INFORMATION, exchange.getProperty(ERROR_INFORMATION, String.class));
        } else {
            variables.put(TRANSFER_STATE, "COMMITTED");
            variables.put(TRANSACTION_FAILED, false);
        }

        zeebeClient.newPublishMessageCommand()
                .messageName(TRANSFER_RESPONSE)
                .correlationKey(exchange.getProperty(TRANSACTION_ID, String.class))
                .timeToLive(Duration.ofMillis(30000))
                .variables(variables)
                .send()
                .join();

    }
}
