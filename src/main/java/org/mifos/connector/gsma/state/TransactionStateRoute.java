package org.mifos.connector.gsma.state;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.mifos.connector.common.gsma.dto.ErrorDTO;
import org.mifos.connector.common.gsma.dto.RequestStateDTO;
import org.mifos.connector.gsma.auth.AccessTokenStore;
import org.mifos.connector.gsma.transfer.CorrelationIDStore;
import org.mifos.connector.gsma.transfer.TransferResponseProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

import static org.mifos.connector.gsma.camel.config.CamelProperties.*;
import static org.mifos.connector.gsma.camel.config.CamelProperties.CORRELATION_ID;
import static org.mifos.connector.gsma.zeebe.ZeebeExpressionVariables.TRANSACTION_FAILED;

@Component
public class TransactionStateRoute extends RouteBuilder {

    @Autowired
    private AccessTokenStore accessTokenStore;

    @Autowired
    private CorrelationIDStore correlationIDStore;

    @Autowired
    private TransferResponseProcessor transferResponseProcessor;

    @Value("${gsma.api.host}")
    private String BaseURL;

    @Value("${gsma.api.channel}")
    private String ChannelURL;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void configure() throws Exception {

        /**
         * Route to check state and assign flags
         */
        from("direct:transaction-state-check")
                .id("transaction-state-check")
                .to("direct:transaction-state")
                .process(exchange -> {
                    if (exchange.getProperty(STATUS_AVAILABLE, Boolean.class)) {
                        if (exchange.getProperty(TRANSACTION_STATUS, String.class).equals("failed")) {
                            exchange.setProperty(ERROR_INFORMATION, exchange.getIn().getBody(RequestStateDTO.class).getError().getErrorDescription());
                            exchange.setProperty(TRANSACTION_FAILED, true);
                        } else if (exchange.getProperty(TRANSACTION_STATUS, String.class).equals("completed")) {
                            exchange.setProperty(TRANSACTION_FAILED, false);
                        }
                    } else {
                        exchange.setProperty(TRANSACTION_FAILED, true);
                    }
                })
                .process(transferResponseProcessor);

        /**
         * Base Route for Transaction State
         */
        from("direct:transaction-state")
                .id("transaction-state")
                .log(LoggingLevel.INFO, "Transaction State route started")
                .to("direct:get-access-token")
                .process(exchange -> exchange.setProperty(ACCESS_TOKEN, accessTokenStore.getAccessToken()))
                .log(LoggingLevel.INFO, "Got access token, moving on.")
                .choice()
                .when(exchange -> correlationIDStore.isClientCorrelationPresent(exchange.getProperty(CORRELATION_ID, String.class)))
                    .log(LoggingLevel.INFO, "Getting Server Correlation ID")
                    .process(exchange -> {
                        Stream<String> serverCorrelations = correlationIDStore.getServerCorrelations(exchange.getProperty(CORRELATION_ID, String.class));
                        String serverCorrelationID = serverCorrelations.findFirst().get();
                        exchange.setProperty(SERVER_CORRELATION, serverCorrelationID);
                        logger.info("Server CorrelationID: " + serverCorrelationID);
                    })
                    .to("direct:get-transaction-state")
                    .log(LoggingLevel.INFO, "Transaction State API response: ${body}")
                    .choice()
                    .when(header("CamelHttpResponseCode").isEqualTo("200"))
                        .to("direct:transaction-state-success")
                    .otherwise()
                        .log(LoggingLevel.INFO, "Transaction State Request Unsuccessful")
                        .to("direct:transaction-state-error")
                    .endChoice()
                .otherwise()
                    .log(LoggingLevel.INFO, "No Server Correlation found for Client")
                    .to("direct:transaction-state-error")
                .endChoice();

        /**
         * Route to call Transaction State API
         */
        from("direct:get-transaction-state")
                .id("get-transaction-state")
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .setHeader("X-Date", simple(ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT )))
                .setHeader("Authorization", simple("Bearer ${exchangeProperty."+ACCESS_TOKEN+"}"))
                .toD(BaseURL + "/requeststates" + "/${exchangeProperty."+SERVER_CORRELATION+"}" + "?bridgeEndpoint=true&throwExceptionOnFailure=false");


        from("direct:get-transaction-state-channel")
                .id("get-transaction-state")
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .toD(ChannelURL + "channel/requeststates" + "/${exchangeProperty."+SERVER_CORRELATION+"}" + "?bridgeEndpoint=true&throwExceptionOnFailure=false");

        /**
         * Error route handler
         * TODO: Improve based on use cases
         */
        from("direct:transaction-state-error")
                .id("transaction-state-error")
                .log(LoggingLevel.INFO, "Error in getting Transaction State")
                .unmarshal().json(JsonLibrary.Jackson, ErrorDTO.class)
                .process(exchange -> {
                    exchange.setProperty(STATUS_AVAILABLE, false);
                    exchange.setProperty(ERROR_INFORMATION, exchange.getIn().getBody(ErrorDTO.class).getErrorDescription());
                });
        /**
         * Success route handler
         */
        from("direct:transaction-state-success")
                .id("transaction-state-success")
                .log(LoggingLevel.INFO, "Transaction State request successful")
                .unmarshal().json(JsonLibrary.Jackson, RequestStateDTO.class)
                .process(exchange -> {
                    String status = exchange.getIn().getBody(RequestStateDTO.class).getStatus();
                    exchange.setProperty(TRANSACTION_STATUS, status);
                    exchange.setProperty(STATUS_AVAILABLE, true);
                });
    }
}
