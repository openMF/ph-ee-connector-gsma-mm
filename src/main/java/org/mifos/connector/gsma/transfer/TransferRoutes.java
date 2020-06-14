package org.mifos.connector.gsma.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.mifos.connector.gsma.auth.dto.AccessTokenStore;
import org.mifos.connector.gsma.identifier.dto.ErrorDTO;
import org.mifos.connector.gsma.transfer.dto.GSMATransaction;
import org.mifos.connector.gsma.transfer.dto.RequestStateDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.mifos.connector.gsma.camel.config.CamelProperties.*;
import static org.mifos.connector.gsma.zeebe.ZeebeExpressionVariables.TRANSACTION_FAILED;

@Component
public class TransferRoutes extends RouteBuilder {

    @Autowired
    private TransferResponseProcessor transferResponseProcessor;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccessTokenStore accessTokenStore;

    @Value("${gsma.api.host}")
    private String BaseURL;

    @Autowired
    private CorrelationIDStore correlationIDStore;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void configure() {

        /**
         * Base route for transactions
         */
        from("direct:transfer-route")
                .id("transfer-route")
                .log(LoggingLevel.INFO, "Transfer route started")
                .to("direct:get-access-token")
                .process(exchange -> exchange.setProperty(ACCESS_TOKEN, accessTokenStore.getAccessToken()))
                .log(LoggingLevel.INFO, "Got access token, moving on to API call.")
                .process(exchange -> {
                    GSMATransaction channelRequest = objectMapper.readValue(exchange.getProperty(TRANSACTION_BODY, String.class), GSMATransaction.class);
                    logger.info("Channel transaction body: " + exchange.getProperty(TRANSACTION_BODY, String.class));
                    exchange.getIn().setBody(channelRequest);
                })
                .to("direct:commit-transaction")
                .log(LoggingLevel.INFO, "Intermediate state: ${body}")
                .choice()
                    .when(header("CamelHttpResponseCode").isEqualTo("202"))
                        .log(LoggingLevel.INFO, "Request was successful. Sending to handler")
                        .unmarshal().json(JsonLibrary.Jackson, RequestStateDTO.class)
                        .process(exchange -> {
                            correlationIDStore.addMapping(exchange.getIn().getBody(RequestStateDTO.class).getServerCorrelationId(),
                                    exchange.getProperty(CORELATION_ID, String.class));
                            logger.info("Saved correlationId mapping.");
                        })
                    .otherwise()
                        .log(LoggingLevel.ERROR, "Transaction request unsuccessful")
//                        .unmarshal().json(JsonLibrary.Jackson, ErrorDTO.class)
                        .process(exchange -> {
//                            exchange.setProperty(TRANSACTION_RESPONSE, exchange.getIn().getBody(ErrorDTO.class).getErrorDescription()); // To be removed
                            exchange.setProperty(TRANSACTION_ID, exchange.getProperty(CORELATION_ID)); // TODO: Improve this, possible take out error handler
                            exchange.setProperty(ERROR_INFORMATION, exchange.getIn(String.class));
//                            exchange.setProperty(TRANSACTION_FAILED, constant(true));
                        })
                        .setProperty(TRANSACTION_FAILED, constant(true))
                        .process(transferResponseProcessor);

        /**
         * Calls GSMA API to commit transaction
         */
        from("direct:commit-transaction")
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader("X-Date", simple(ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT )))
                .setHeader("Authorization", simple("Bearer ${exchangeProperty."+ACCESS_TOKEN+"}"))
                .setHeader("X-Callback-URL", constant("http://4331aa8ed91b.ngrok.io/transfer/callback")) // TODO: Remove hard coded value
                .setHeader("X-CorrelationID", simple("${exchangeProperty."+CORELATION_ID+"}"))
                .setBody(exchange -> exchange.getProperty(TRANSACTION_BODY, GSMATransaction.class))
                .marshal().json(JsonLibrary.Jackson)
                .toD(BaseURL + "/transactions" + "?bridgeEndpoint=true&throwExceptionOnFailure=false");

        /**
         * Callback for transaction
         */
        from("rest:PUT:/transfer/callback")
                .log(LoggingLevel.INFO, "Callback body ${body}")
                .unmarshal().json(JsonLibrary.Jackson, RequestStateDTO.class)
                .process(exchange -> {
                    String serverUUID = exchange.getIn().getBody(RequestStateDTO.class).getServerCorrelationId();
                    exchange.setProperty(TRANSACTION_ID, correlationIDStore.getClientCorrelation(serverUUID));
                })
                .choice()
                    .when(exchange -> exchange.getIn().getBody(RequestStateDTO.class).getStatus().equals("completed"))
                        .setProperty(TRANSACTION_FAILED, constant(false))
//                        .process(exchange -> {
//                            exchange.setProperty(TRANSACTION_FAILED, constant(false));
//                        })
                    .otherwise()
                        .setProperty(TRANSACTION_FAILED, constant(false))
                        .process(exchange -> {
                            exchange.setProperty(ERROR_INFORMATION, exchange.getIn().getBody(RequestStateDTO.class).toString());
//                            exchange.setProperty(TRANSACTION_FAILED, constant(true));
                        })
                .end()
                .process(transferResponseProcessor);
//                .process(exchange -> {
//                    String serverUUID = exchange.getIn().getBody(RequestStateDTO.class).getServerCorrelationId();
//                    logger.info("Client corelationId is: " + correlationIDStore
//                            .getClientCorrelation(serverUUID));
//                    logger.info("Request status is: " + exchange.getIn().getBody(RequestStateDTO.class).getStatus());
//                });

    }
}