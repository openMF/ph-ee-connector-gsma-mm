package org.mifos.connector.gsma.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.mifos.connector.gsma.auth.dto.AccessTokenStore;
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
    private TransformRequestDataProcessor transformRequestDataProcessor;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccessTokenStore accessTokenStore;

    @Value("${gsma.api.host}")
    private String BaseURL;

    @Value("${camel.host}")
    private String HostURL;

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
                .log(LoggingLevel.INFO, "Got access token, moving on to transform data")
                .process(transformRequestDataProcessor)
                .log(LoggingLevel.INFO, "Moving on to API call")
                .to("direct:commit-transaction")
                .log(LoggingLevel.INFO, "Transaction API response: ${body}")
                .choice()
                    .when(header("CamelHttpResponseCode").isEqualTo("202"))
                        .log(LoggingLevel.INFO, "Transaction request successful")
                        .unmarshal().json(JsonLibrary.Jackson, RequestStateDTO.class)
                        .process(exchange -> {
                            correlationIDStore.addMapping(exchange.getIn().getBody(RequestStateDTO.class).getServerCorrelationId(),
                                    exchange.getProperty(CORRELATION_ID, String.class));
                            logger.info("Saved correlationId mapping");
                        })
                    .otherwise()
                        .log(LoggingLevel.ERROR, "Transaction request unsuccessful")
                        .process(exchange -> {
                            exchange.setProperty(TRANSACTION_ID, exchange.getProperty(CORRELATION_ID)); // TODO: Improve this
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
                .setHeader("X-Callback-URL", simple(HostURL + "/transfer/callback")) // TODO: Remove hard coded value
                .setHeader("X-CorrelationID", simple("${exchangeProperty."+ CORRELATION_ID +"}"))
                .setHeader("Content-Type", constant("application/json"))
                .setBody(exchange -> exchange.getProperty(TRANSACTION_BODY))
                .marshal().json(JsonLibrary.Jackson)
                .log(LoggingLevel.INFO, "Transaction Request Body: ${body}")
                .toD(BaseURL + "/transactions/type" + "/${exchangeProperty."+TRANSACTION_TYPE+"}" + "?bridgeEndpoint=true&throwExceptionOnFailure=false");

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
                    .otherwise()
                        .setProperty(TRANSACTION_FAILED, constant(true))
                .end()
                .process(transferResponseProcessor);

    }
}
