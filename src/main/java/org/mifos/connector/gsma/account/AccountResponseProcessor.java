package org.mifos.connector.gsma.account;

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

import static org.mifos.connector.gsma.camel.config.CamelProperties.CORRELATION_ID;
import static org.mifos.connector.gsma.camel.config.CamelProperties.ERROR_INFORMATION;
import static org.mifos.connector.gsma.zeebe.ZeebeExpressionVariables.PARTY_LOOKUP_FAILED;
import static org.mifos.connector.gsma.zeebe.ZeebeMessages.ACCOUNT_STATUS;

@Component
public class AccountResponseProcessor implements Processor {

    @Autowired
    private ZeebeClient zeebeClient;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${zeebe.client.ttl}")
    private int timeToLive;

    @Override
    public void process(Exchange exchange) {

        Map<String, Object> variables = new HashMap<>();

        Object isPayeePartyLookupFailed = exchange.getProperty(PARTY_LOOKUP_FAILED);
        if (isPayeePartyLookupFailed != null && (boolean) isPayeePartyLookupFailed) {
            variables.put(ERROR_INFORMATION, exchange.getIn().getBody(String.class));
            variables.put(PARTY_LOOKUP_FAILED, true);
        } else {
//            TODO: Consult and Add partyLookupFspId
            variables.put(PARTY_LOOKUP_FAILED, false);
        }

        logger.info("Publishing account status message variables: " + variables);

        zeebeClient.newPublishMessageCommand()
                .messageName(ACCOUNT_STATUS)
                .correlationKey(exchange.getProperty(CORRELATION_ID, String.class))
                .timeToLive(Duration.ofMillis(timeToLive))
                .variables(variables)
                .send()
                .join();
    }
}
