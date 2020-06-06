package org.mifos.connector.gsma.identifier;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.mifos.connector.gsma.zeebe.ZeebeProcessStarter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IdentifierLookupRoutes extends RouteBuilder {

    @Autowired
    private ZeebeProcessStarter zeebeProcessStarter;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void configure() {
        from("direct:sample-route")
                .id("sample-route")
                .log(LoggingLevel.INFO, "######## Route is starting")
                .end();
    }
}