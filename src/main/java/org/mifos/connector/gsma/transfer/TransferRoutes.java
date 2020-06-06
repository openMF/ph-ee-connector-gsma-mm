package org.mifos.connector.gsma.transfer;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class TransferRoutes extends RouteBuilder {

    @Override
    public void configure() {
        from("direct:sample-route")
                .id("sample-route")
                .log(LoggingLevel.INFO, "######## Route is starting")
                .end();
    }
}