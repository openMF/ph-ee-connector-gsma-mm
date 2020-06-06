package org.mifos.connector.gsma.transfer;

import org.apache.camel.LoggingLevel;
import org.mifos.connector.common.camel.ErrorHandlerRouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class TransferRoutes extends ErrorHandlerRouteBuilder {

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