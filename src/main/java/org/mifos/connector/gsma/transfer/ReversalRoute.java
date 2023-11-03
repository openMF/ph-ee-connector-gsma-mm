package org.mifos.connector.gsma.transfer;

import static org.mifos.connector.gsma.camel.config.CamelProperties.ACCESS_TOKEN;
import static org.mifos.connector.gsma.camel.config.CamelProperties.CORRELATION_ID;
import static org.mifos.connector.gsma.camel.config.CamelProperties.RESPONSE_OBJECT;
import static org.mifos.connector.gsma.camel.config.CamelProperties.RESPONSE_OBJECT_AVAILABLE;
import static org.mifos.connector.gsma.camel.config.CamelProperties.RESPONSE_OBJECT_TYPE;
import static org.mifos.connector.gsma.camel.config.CamelProperties.TRANSACTION_BODY;
import static org.mifos.connector.gsma.camel.config.CamelProperties.TRANSACTION_REFERENCE;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.mifos.connector.common.gsma.dto.GSMATransaction;
import org.mifos.connector.gsma.auth.AccessTokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReversalRoute extends RouteBuilder {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccessTokenStore accessTokenStore;

    @Value("${gsma.api.host}")
    private String baseURL;

    @Autowired
    private CorrelationIDStore correlationIDStore;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void configure() throws Exception {

        /**
         * Reversal Starter with client correlation ID
         */
        from("direct:reversal-starter").id("reversal-starter").setProperty(RESPONSE_OBJECT_TYPE, constant("transaction"))
                .to("direct:response-route").choice().when(exchange -> exchange.getProperty(RESPONSE_OBJECT_AVAILABLE, Boolean.class))
                .process(exchange -> exchange.setProperty(TRANSACTION_REFERENCE,
                        exchange.getProperty(RESPONSE_OBJECT, GSMATransaction.class).getTransactionReference()))
                .to("direct:reversal-base-route").otherwise().log("Failed to get transaction reference");

        /**
         * Base route for reversal with transaction reference
         */
        from("direct:reversal-base-route").id("reversal-base-route").log(LoggingLevel.INFO, "Reversal route started")
                .to("direct:get-access-token").process(exchange -> exchange.setProperty(ACCESS_TOKEN, accessTokenStore.getAccessToken()))
                .log(LoggingLevel.INFO, "Got access token, moving on to get transaction reference").process(exchange -> {
                    GSMATransaction reversalBody = new GSMATransaction();
                    reversalBody.setType("reversal");
                    exchange.setProperty(TRANSACTION_BODY, reversalBody);
                }).to("direct:commit-reversal").log(LoggingLevel.INFO, "Reversal Response: ${body}").choice()
                .when(header("CamelHttpResponseCode").isEqualTo("201")).log(LoggingLevel.INFO, "Reversal successful").otherwise()
                .log(LoggingLevel.INFO, "Reversal transaction failed").endChoice();

        /**
         * Call GSMA APIs to commit reversal
         */
        from("direct:commit-reversal").removeHeader("*").setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader("X-Date", simple(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)))
                .setHeader("Authorization", simple("Bearer ${exchangeProperty." + ACCESS_TOKEN + "}"))
                .setHeader("Content-Type", constant("application/json")).setBody(exchange -> exchange.getProperty(TRANSACTION_BODY))
                .marshal().json(JsonLibrary.Jackson).log(LoggingLevel.INFO, "Transaction Request Body: ${body}")
                .toD(baseURL + "/transactions" + "/${exchangeProperty." + TRANSACTION_REFERENCE + "}" + "/reversals"
                        + "?bridgeEndpoint=true&throwExceptionOnFailure=false");

        /**
         * API to start reversal
         */
        from("rest:GET:/transaction/reverse/{correlation}").log(LoggingLevel.INFO, "Transaction Reverse API").process(exchange -> {
            exchange.setProperty(CORRELATION_ID, exchange.getIn().getHeader("correlation"));
        }).to("direct:reversal-starter");
    }
}
