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
                .setBody(constant("GET Good"));

        from("rest:POST:/")
                .log(LoggingLevel.INFO, "POST Body: ${body}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                .setBody(constant("All Post Good"));

        from("rest:POST:/transfer")
                .process(exchange -> {
                    exchange.setProperty(CORELATION_ID, UUID.randomUUID().toString());
                    exchange.setProperty(CHANNEL_REQUEST, exchange.getIn().getBody(String.class));
                    exchange.setProperty(IS_RTP_REQUEST, false);
                    logger.info("Client Correlation ID: " + exchange.getProperty(CORELATION_ID, String.class));
                })
                .to("direct:transfer-route");

        from("rest:GET:/transfer/{" + TRANSACTION_ID + "}")
                .process(exchange -> {
                    exchange.setProperty(CORELATION_ID, exchange.getIn().getHeader(TRANSACTION_ID, String.class));
                })
                .to("direct:transaction-state")
                .process(exchange -> {
                    logger.info("Status Available: " + exchange.getProperty(STATUS_AVAILABLE, Boolean.class));
                    if (exchange.getProperty(STATUS_AVAILABLE, Boolean.class))
                        logger.info("Status is: " + exchange.getProperty(TRANSACTION_STATUS, String.class));
                });
    }
}
