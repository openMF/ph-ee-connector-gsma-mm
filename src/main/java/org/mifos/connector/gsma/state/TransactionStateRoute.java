package org.mifos.connector.gsma.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.mifos.connector.gsma.auth.dto.AccessTokenStore;
import org.mifos.connector.gsma.transfer.CorrelationIDStore;
import org.mifos.connector.gsma.transfer.dto.RequestStateDTO;
import org.mifos.connector.gsma.zeebe.ZeebeProcessStarter;
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
import static org.mifos.connector.gsma.camel.config.CamelProperties.CORELATION_ID;
import static org.mifos.connector.gsma.zeebe.ZeebeExpressionVariables.TRANSACTION_FAILED;

@Component
public class TransactionStateRoute extends RouteBuilder {

    @Autowired
    private ZeebeProcessStarter zeebeProcessStarter;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccessTokenStore accessTokenStore;

    @Autowired
    private CorrelationIDStore correlationIDStore;

    @Value("${gsma.api.host}")
    private String BaseURL;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void configure() throws Exception {

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
                .when(exchange -> correlationIDStore.isClientCorrelationPresent(exchange.getProperty(CORELATION_ID, String.class)))
                    .log(LoggingLevel.INFO, "Getting Server Correlation ID")
                    .process(exchange -> {
                        Stream<String> serverCorrelations = correlationIDStore.getServerCorrelations(exchange.getProperty(CORELATION_ID, String.class));
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

        /**
         * Error route handler
         */
        from("direct:transaction-state-error")
                .id("transaction-state-error")
                .log(LoggingLevel.INFO, "Error in getting Transaction State")
                .setProperty(STATUS_AVAILABLE, constant(false));

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
