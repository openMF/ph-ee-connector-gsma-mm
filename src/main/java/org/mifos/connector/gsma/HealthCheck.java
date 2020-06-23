package org.mifos.connector.gsma;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.mifos.connector.gsma.auth.dto.AccessTokenStore;
import org.mifos.connector.gsma.identifier.dto.AccountStatusRequest;
import org.mifos.connector.gsma.zeebe.ZeebeProcessStarter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

        from("rest:GET:/deploy")
                .to("direct:test-bpmn")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200));

        from("direct:test-bpmn")
                .id("test-bpmn")
                .process(exchange -> {
                    zeebeProcessStarter.startZeebeWorkflow("sampleProcess");
                });

        from("rest:POST:/account/{accountAction}")
                .unmarshal().json(JsonLibrary.Jackson, AccountStatusRequest.class)
                .process(exchange -> {
                    exchange.setProperty(IDENTIFIER_TYPE, exchange.getIn().getBody(AccountStatusRequest.class).getIdentifierType());
                    exchange.setProperty(IDENTIFIER, exchange.getIn().getBody(AccountStatusRequest.class).getIdentifier());
                    exchange.setProperty(ACCOUNT_ACTION, exchange.getIn().getHeader("accountAction"));
                })
                .to("direct:get-access-token")
                .process(exchange -> exchange.setProperty(ACCESS_TOKEN, accessTokenStore.getAccessToken()))
                .log(LoggingLevel.INFO, "Got access token, moving on to API call.")
                .to("direct:account-route")
                .setBody(exchange -> exchange.getProperty(ACCOUNT_RESPONSE, String.class));
    }
}
