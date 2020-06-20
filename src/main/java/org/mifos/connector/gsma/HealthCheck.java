package org.mifos.connector.gsma;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.mifos.connector.gsma.auth.dto.AccessTokenStore;
import org.mifos.connector.gsma.identifier.dto.AccountStatusRequest;
import org.mifos.connector.gsma.transfer.dto.GSMATransaction;
import org.mifos.connector.gsma.zeebe.ZeebeProcessStarter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mifos.connector.gsma.camel.config.CamelProperties.*;

@Component
public class HealthCheck extends RouteBuilder {

    @Autowired
    public ZeebeProcessStarter zeebeProcessStarter;

    @Autowired
    public AccessTokenStore accessTokenStore;

    private static final Logger logger = LoggerFactory.getLogger(ZeebeProcessStarter.class);

    @Override
    public void configure() throws Exception {
        from("rest:GET:/")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                .setBody(constant("All Good"));

        from("rest:GET:/accesstoken")
                .to("direct:get-access-token")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                .setBody(exchange -> accessTokenStore.getAccessToken())
                .process(exchange -> {
                    logger.info("Access token: " + accessTokenStore.getAccessToken());
                });

        from("rest:POST:/account/{accountAction}")
                .process(exchange -> {
                    exchange.setProperty(CORELATION_ID, generateUUID());
                    exchange.setProperty(TRANSACTION_BODY, exchange.getIn().getBody(String.class));
                    exchange.setProperty(ACCOUNT_ACTION, exchange.getIn().getHeader("accountAction")); // Get's hardcoded in Zeebe Worker
                })
                .to("direct:account-route")
                .setBody(exchange -> exchange.getProperty(ACCOUNT_RESPONSE, String.class));

        from("rest:POST:/transfer")
                .process(exchange -> {
                    exchange.setProperty(CORELATION_ID, generateUUID());
                    exchange.setProperty(TRANSACTION_BODY, exchange.getIn().getBody(String.class));
                })
                .to("direct:transfer-route");

        from("rest:POST:/zeebe/test/deploy")
                .process(exchange -> {
                    Map<String, Object> variables = new HashMap<>();
                    zeebeProcessStarter.startZeebeWorkflow("sampleProcess", variables);
                })
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200));

        from("rest:POST:/zeebe/test/transaction")
                .process(exchange -> {
                    Map<String, Object> variables = new HashMap<>();
                    variables.put("transactionId", generateUUID());
                    variables.put("channelBody", exchange.getIn().getBody(String.class));
                    zeebeProcessStarter.startZeebeWorkflow("transactionTester", variables);
                });

        from("rest:POST:/zeebe/transfer")
                .process(exchange -> {
                    Map<String, Object> variables = new HashMap<>();
                    variables.put("transactionId", generateUUID());
                    variables.put("channelBody", exchange.getIn().getBody(String.class));
                    zeebeProcessStarter.startZeebeWorkflow("gsma_p2p_base", variables);
                });
    }

    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }

}
