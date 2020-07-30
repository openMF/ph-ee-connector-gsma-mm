package org.mifos.connector.gsma;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.mifos.connector.gsma.auth.dto.AccessTokenStore;
import org.mifos.connector.gsma.zeebe.ZeebeProcessStarter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

    }
}
