package org.mifos.connector.gsma.identifier;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.mifos.connector.gsma.auth.dto.AccessTokenStore;
import org.mifos.connector.gsma.identifier.dto.AccountStatusError;
import org.mifos.connector.gsma.identifier.dto.AccountStatusResponse;
import org.mifos.connector.gsma.zeebe.ZeebeProcessStarter;
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
public class IdentifierLookupRoutes extends RouteBuilder {

    @Autowired
    private ZeebeProcessStarter zeebeProcessStarter;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccessTokenStore accessTokenStore;

    @Value("${gsma.api.host}")
    private String BaseURL;

    @Value("${gsma.api.account}")
    private String account;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void configure() throws Exception {

        /**
         * Error handling route
         */
        from("direct:account-status-error")
                .id("account-status-error")
                .unmarshal().json(JsonLibrary.Jackson, AccountStatusError.class)
                .process(exchange -> {
                    exchange.setProperty(ACCOUNT_STATUS_RESPONSE, exchange.getIn().getBody(AccountStatusError.class).getErrorDescription());
                    logger.error(exchange.getIn().getBody(AccountStatusError.class).toString());
//                    TODO: Improve Error Handling. Possibly publish errorDescription and fail transaction.
                });

        /**
         * Route when account is available
         */
        from("direct:account-status-success")
                .id("account-status-success")
                .unmarshal().json(JsonLibrary.Jackson, AccountStatusResponse.class)
                .process(exchange -> {
                    exchange.setProperty(ACCOUNT_STATUS_RESPONSE, exchange.getIn().getBody(AccountStatusResponse.class).getAccountStatus());
//                    TODO: Publish available in Zeebe message
                });

        /**
         * Route to call GSMA API
         */
        from("direct:get-account-status")
                .id("get-account-status")
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .setHeader("X-Date", simple(ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT )))
                .setHeader("Authorization", simple("Bearer ${exchangeProperty."+ACCESS_TOKEN+"}"))
                .toD(BaseURL + account + "/${exchangeProperty."+IDENTIFIER_TYPE+"}/${exchangeProperty."+IDENTIFIER+"}/status?bridgeEndpoint=true&throwExceptionOnFailure=false");

        /**
         * Main route for account status
         */
        from("direct:account-status")
                .id("account-status")
                .log(LoggingLevel.INFO, "Getting account status for Identifier")
                .to("direct:get-account-status")
                .log(LoggingLevel.INFO, "Completed Account Status call ${body}")
                .choice()
                    .when(header("CamelHttpResponseCode").isEqualTo("200"))
                        .to("direct:account-status-success")
                    .otherwise()
                        .to("direct:account-status-error");
    }
}