package org.mifos.connector.gsma.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.mifos.connector.common.channel.dto.TransactionChannelRequestDTO;
import org.mifos.connector.gsma.account.dto.DebitMandateDTO;
import org.mifos.connector.gsma.account.dto.ErrorDTO;
import org.mifos.connector.gsma.auth.dto.AccessTokenStore;
import org.mifos.connector.gsma.transfer.CorrelationIDStore;
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
public class DebitMandateRoute extends RouteBuilder {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccessTokenStore accessTokenStore;

    @Autowired
    private CorrelationIDStore correlationIDStore;

    @Value("${gsma.api.host}")
    private String BaseURL;

    @Value("${gsma.api.account}")
    private String account;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void configure() throws Exception {

        /**
         * Base Route for debit mandate
         */
        from("direct:debit-mandate-base")
                .id("debit-mandate-base")
                .log(LoggingLevel.INFO, "Debit Mandate route started")
                .to("direct:get-access-token")
                .process(exchange -> exchange.setProperty(ACCESS_TOKEN, accessTokenStore.getAccessToken()))
                .log(LoggingLevel.INFO, "Got access token, moving on.")
//                TODO: Create request body
                .to("direct:create-debit-mandate")
                .log(LoggingLevel.INFO, "Debit Mandate Response: ${body}")
                .choice()
                .when(header("CamelHttpResponseCode").isEqualTo("201"))
                    .log(LoggingLevel.INFO, "Mandate creation request successful")
                    .unmarshal().json(JsonLibrary.Jackson, DebitMandateDTO.class)
                    .process(exchange -> {
                        exchange.setProperty(MANDATE_CREATE_FAILED, false);
                        exchange.setProperty(MANDATE_REFERENCE, exchange.getIn().getBody(DebitMandateDTO.class).getMandateReference());
                    })
                .otherwise()
                    .log(LoggingLevel.INFO, "Mandate creation failed")
                    .unmarshal().json(JsonLibrary.Jackson, ErrorDTO.class)
                    .process(exchange -> {
                        exchange.setProperty(MANDATE_CREATE_FAILED, true);
                        exchange.setProperty(ERROR_INFORMATION, exchange.getIn().getBody(ErrorDTO.class).getErrorCode());
                    })
                .endChoice();

        /**
         * Calling API for debit mandate
         */
        from("direct:create-debit-mandate")
                .id("create-debit-mandate")
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader("X-Date", simple(ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT )))
                .setHeader("Authorization", simple("Bearer ${exchangeProperty."+ACCESS_TOKEN+"}"))
                .setHeader("Content-Type", constant("application/json"))
                .setBody(exchange -> exchange.getProperty(DEBIT_MANDATE_BODY))
                .marshal().json(JsonLibrary.Jackson)
                .log(LoggingLevel.INFO, "Debit Mandate Request Body: ${body}")
                .toD(BaseURL + account + "/${exchangeProperty."+IDENTIFIER_TYPE+"}/${exchangeProperty."+IDENTIFIER+"}/debitmandates?bridgeEndpoint=true&throwExceptionOnFailure=false");

        /**
         * API to create debit mandate
         */
        from("rest:POST:/account/debitmandate/{identifier_type}/{identifier}")
                .log(LoggingLevel.INFO, "Creating Debit Mandate")
                .process(exchange -> {
                    exchange.setProperty(IDENTIFIER_TYPE, exchange.getIn().getHeader("identifier_type"));
                    exchange.setProperty(IDENTIFIER, exchange.getIn().getHeader("identifier"));

                    TransactionChannelRequestDTO channelRequest = objectMapper.readValue(exchange.getIn().getBody(String.class), TransactionChannelRequestDTO.class);

                    DebitMandateDTO debitMandateRequest = new DebitMandateDTO();

                    debitMandateRequest.setRequestDate(ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT ));
                    debitMandateRequest.setStartDate(ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT ));
                    debitMandateRequest.setAmountLimit(channelRequest.getAmount().getAmount());
                    debitMandateRequest.setCurrency(channelRequest.getAmount().getCurrency());
                    debitMandateRequest.setNumberOfPayments(10);

                    exchange.setProperty(DEBIT_MANDATE_BODY, debitMandateRequest);
                })
                .to("direct:debit-mandate-base")
                .setBody(simple("${exchangeProperty."+MANDATE_REFERENCE+"}"));

    }
}
