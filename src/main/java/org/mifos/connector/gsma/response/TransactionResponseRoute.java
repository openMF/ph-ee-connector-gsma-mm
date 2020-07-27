package org.mifos.connector.gsma.response;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.mifos.connector.gsma.auth.dto.AccessTokenStore;
import org.mifos.connector.gsma.response.dto.TransactionResponseLinkDTO;
import org.mifos.connector.gsma.transfer.CorrelationIDStore;
import org.mifos.connector.gsma.transfer.dto.GSMATransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.mifos.connector.gsma.camel.config.CamelProperties.*;

@Component
public class TransactionResponseRoute extends RouteBuilder {

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
         * Base route for transaction response
         */
        from("direct:transaction-response")
                .id("transaction-response")
                .log(LoggingLevel.INFO, "Transaction Response route started")
                .to("direct:get-access-token")
                .process(exchange -> exchange.setProperty(ACCESS_TOKEN, accessTokenStore.getAccessToken()))
                .log(LoggingLevel.INFO, "Got access token, moving on.")
                .to("direct:get-transaction-link")
                .log(LoggingLevel.INFO, "Transaction Response Link: ${body}")
                .choice()
                    .when(header("CamelHttpResponseCode").isEqualTo("200"))
                    .unmarshal().json(JsonLibrary.Jackson, TransactionResponseLinkDTO.class)
                    .process(exchange -> exchange.setProperty(TRANSACTION_LINK, exchange.getIn().getBody(TransactionResponseLinkDTO.class).getLink()))
                    .marshal().json(JsonLibrary.Jackson)
                    .log(LoggingLevel.INFO, "Moving to get Transaction Object")
                    .to("direct:get-link-object")
                    .log(LoggingLevel.INFO, "Transaction Link Object Response: ${body}")
                    .choice()
                        .when(header("CamelHttpResponseCode").isEqualTo("200"))
                            .unmarshal().json(JsonLibrary.Jackson, GSMATransaction.class)
                            .process(exchange -> {
                                exchange.setProperty(TRANSACTION_OBJECT_AVAILABLE, true);
                                exchange.setProperty(TRANSACTION_OBJECT, exchange.getIn().getBody(GSMATransaction.class));
                            })
                        .otherwise()
                            .log(LoggingLevel.INFO, "Error in getting Transaction Object")
                            .to("direct:transaction-response-error")
                    .endChoice()
                    .otherwise()
                        .log(LoggingLevel.INFO, "Error in getting Transaction Response Link")
                        .to("direct:transaction-response-error")
                .endChoice();

        /**
         * Route to get Transaction Response Link API
         */
        from("direct:get-transaction-link")
                .id("get-transaction-link")
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .setHeader("X-Date", simple(ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT )))
                .setHeader("Authorization", simple("Bearer ${exchangeProperty."+ACCESS_TOKEN+"}"))
                .toD(BaseURL + "/responses" + "/${exchangeProperty."+ CORRELATION_ID +"}" + "?bridgeEndpoint=true&throwExceptionOnFailure=false");

        /**
         * Route to get Transaction Object
         */
        from("direct:get-link-object")
                .id("get-link-object")
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .setHeader("X-Date", simple(ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT )))
                .setHeader("Authorization", simple("Bearer ${exchangeProperty."+ACCESS_TOKEN+"}"))
                .toD(BaseURL + "/${exchangeProperty."+TRANSACTION_LINK+"}" + "?bridgeEndpoint=true&throwExceptionOnFailure=false");

        /**
         * Error Handler Route for Transaction Response
         * TODO: Improve based on use cases
         */
        from("direct:transaction-response-error")
                .id("transaction-response-error")
                .log(LoggingLevel.INFO, "Error in getting Transaction Response")
                .setProperty(TRANSACTION_OBJECT_AVAILABLE, constant(false));


    }
}
