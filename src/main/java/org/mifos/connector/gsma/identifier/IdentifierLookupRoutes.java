package org.mifos.connector.gsma.identifier;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.LoggingLevel;
import org.mifos.connector.common.camel.ErrorHandlerRouteBuilder;
import org.mifos.connector.gsma.zeebe.ZeebeProcessStarter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IdentifierLookupRoutes extends ErrorHandlerRouteBuilder {

    @Autowired
    private ZeebeProcessStarter zeebeProcessStarter;

    @Autowired
    private ObjectMapper objectMapper;

    public IdentifierLookupRoutes() {
        super.configure();
    }

    @Override
    public void configure() {
        from("direct:sample-route")
                .id("sample-route")
                .log(LoggingLevel.INFO, "######## Route is starting")
                .process(e -> {
                })
                .end();
    }
}