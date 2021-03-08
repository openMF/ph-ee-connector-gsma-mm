package org.mifos.connector.gsma.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.mifos.connector.common.channel.dto.TransactionChannelRequestDTO;
import org.mifos.connector.common.gsma.dto.BillPaymentDTO;
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
import java.util.UUID;

import static org.mifos.connector.gsma.camel.config.CamelProperties.*;
import static org.mifos.connector.gsma.zeebe.ZeebeExpressionVariables.TRANSACTION_FAILED;

@Component
public class BillsRoute extends RouteBuilder {

    @Autowired
    private CorrelationIDStore correlationIDStore;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccessTokenStore accessTokenStore;

    @Value("${gsma.api.host}")
    private String BaseURL;

    @Value("${gsma.api.account}")
    private String account;

    @Value("${camel.host}")
    private String HostURL;

    @Autowired
    private TransferResponseProcessor transferResponseProcessor;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void configure() throws Exception {

        /**
         * Starter route for all bills actions
         */
        from("direct:bills-route-base")
                .id("bills-route-base")
                .log(LoggingLevel.INFO, "Starting bills ${exchangeProperty."+BILLS_ACTION+"} for Identifier")
                .to("direct:get-access-token")
                .process(exchange -> exchange.setProperty(ACCESS_TOKEN, accessTokenStore.getAccessToken()))
                .log(LoggingLevel.INFO, "Got access token, moving on to API call.")
                .choice()
                .when(exchange -> exchange.getProperty(BILLS_ACTION, String.class).equals("companies"))
                    .to("direct:get-bills-companies")
                    .log(LoggingLevel.INFO, "Bill Companies API response: ${body}")
                    .choice()
                    .when(header("CamelHttpResponseCode").isEqualTo("200"))
                        .process(exchange -> {
                            exchange.setProperty(BILLS_CALL_FAILED, false);
                            exchange.setProperty(BILL_COMPANIES, exchange.getIn().getBody(String.class));
                        })
                    .otherwise()
                        .to("direct:bills-error-handler")
                    .endChoice()
                .when(exchange -> exchange.getProperty(BILLS_ACTION, String.class).equals("bills"))
                    .to("direct:get-bills")
                    .log(LoggingLevel.INFO, "Bills API response: ${body}")
                    .choice()
                    .when(header("CamelHttpResponseCode").isEqualTo("200"))
                        .process(exchange -> {
                            exchange.setProperty(BILLS_CALL_FAILED, false);
                            exchange.setProperty(BILLS, exchange.getIn().getBody(String.class));
                        })
                    .otherwise()
                        .to("direct:bills-error-handler")
                    .endChoice()
                .when(exchange -> exchange.getProperty(BILLS_ACTION, String.class).equals("payment"))
                    .to("direct:bills-payment")
                    .log(LoggingLevel.INFO, "Transaction API response: ${body}")
                    .to("direct:transaction-response-handler")
                .otherwise()
                    .log(LoggingLevel.INFO, "No suitable bill action found")
                .end();

        /**
         * Bills error handler
         */
        from("direct:bills-error-handler")
                .id("bills-error-handler")
                .log(LoggingLevel.INFO, "Error in bills request")
                .unmarshal().json(JsonLibrary.Jackson, ErrorDTO.class)
                .process(exchange -> {
                    exchange.setProperty(BILLS_CALL_FAILED, true);
                    exchange.setProperty(ERROR_INFORMATION, exchange.getIn().getBody(ErrorDTO.class).toString());
                });

        /**
         * API call to get bill companies for an account
         */
        from("direct:get-bills-companies")
                .id("get-bills-companies")
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .setHeader("X-Date", simple(ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT )))
                .setHeader("Authorization", simple("Bearer ${exchangeProperty."+ACCESS_TOKEN+"}"))
                .toD(BaseURL + account + "/${exchangeProperty."+IDENTIFIER_TYPE+"}/${exchangeProperty."+IDENTIFIER+"}" + "/billcompanies" + "?bridgeEndpoint=true&throwExceptionOnFailure=false");

        /**
         * API call to get bills for an account
         */
        from("direct:get-bills")
                .id("get-bills")
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .setHeader("X-Date", simple(ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT )))
                .setHeader("Authorization", simple("Bearer ${exchangeProperty."+ACCESS_TOKEN+"}"))
                .toD(BaseURL + account + "/${exchangeProperty."+IDENTIFIER_TYPE+"}/${exchangeProperty."+IDENTIFIER+"}" + "/bills" + "?bridgeEndpoint=true&throwExceptionOnFailure=false");

        /**
         * API call to initiate payment for bill
         */
        from("direct:bills-payment")
                .id("bills-payment")
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader("X-Date", simple(ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT )))
                .setHeader("Authorization", simple("Bearer ${exchangeProperty."+ACCESS_TOKEN+"}"))
                .setHeader("X-Callback-URL", simple(HostURL + "/bills/payment/callback"))
                .setHeader("X-CorrelationID", simple("${exchangeProperty."+ CORRELATION_ID +"}"))
                .setHeader("Content-Type", constant("application/json"))
                .setBody(exchange -> exchange.getProperty(BILLS_REQUEST_BODY))
                .marshal().json(JsonLibrary.Jackson)
                .log(LoggingLevel.INFO, "Links Request Body: ${body}")
                .toD(BaseURL + account + "/${exchangeProperty."+IDENTIFIER_TYPE+"}/${exchangeProperty."+IDENTIFIER+"}/bills/${exchangeProperty."+BILL_REFERENCE+"}/payments?bridgeEndpoint=true&throwExceptionOnFailure=false");

        /**
         * Callback for bill payment
         */
        from("rest:PUT:/bills/payment/callback")
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
                    .process(exchange -> exchange.setProperty(ERROR_INFORMATION, exchange.getIn().getBody(RequestStateDTO.class).getError().getErrorDescription()))
                    .setProperty(TRANSACTION_FAILED, constant(true))
                .end()
                .process(transferResponseProcessor);

        /**
         * API to initiate bill payment
         */
        from("rest:POST:/bills/payment")
                .log(LoggingLevel.INFO, "Got bill payment POST request")
                .process(exchange -> {

                    exchange.setProperty(BILLS_ACTION, "payment");

                    TransactionChannelRequestDTO channelRequest = objectMapper.readValue(exchange.getIn().getBody(String.class), TransactionChannelRequestDTO.class);
                    exchange.setProperty(BILL_REFERENCE, channelRequest.getPayee().getPartyIdInfo().getPartyIdentifier());
                    exchange.setProperty(IDENTIFIER_TYPE, channelRequest.getPayer().getPartyIdInfo().getPartyIdType());
                    exchange.setProperty(IDENTIFIER, channelRequest.getPayer().getPartyIdInfo().getPartyIdentifier());

                    BillPaymentDTO paymentDTO = new BillPaymentDTO();

                    paymentDTO.setAmountPaid(channelRequest.getAmount().getAmount());
                    paymentDTO.setCurrency(channelRequest.getAmount().getCurrency());

                    exchange.setProperty(BILLS_REQUEST_BODY, paymentDTO);
                    exchange.setProperty(CORRELATION_ID, UUID.randomUUID());
                })
                .to("direct:bills-route-base");

        /**
         * API to get bills for an account
         */
        from("rest:GET:/account/bills/{identifier_type}/{identifier}")
                .log(LoggingLevel.INFO, "Getting Account Bills")
                .process(exchange -> {
                    exchange.setProperty(IDENTIFIER_TYPE, exchange.getIn().getHeader("identifier_type"));
                    exchange.setProperty(IDENTIFIER, exchange.getIn().getHeader("identifier"));
                    exchange.setProperty(BILLS_ACTION, "bills");
                })
                .to("direct:bills-route-base")
                .setBody(exchange -> exchange.getProperty(BILLS, String.class));

        /**
         * API to get bill companies associated with an account
         */
        from("rest:GET:/account/billcompanies/{identifier_type}/{identifier}")
                .log(LoggingLevel.INFO, "Getting Account Status")
                .process(exchange -> {
                    exchange.setProperty(IDENTIFIER_TYPE, exchange.getIn().getHeader("identifier_type"));
                    exchange.setProperty(IDENTIFIER, exchange.getIn().getHeader("identifier"));
                    exchange.setProperty(BILLS_ACTION, "companies");
                })
                .to("direct:bills-route-base")
                .setBody(exchange -> exchange.getProperty(BILL_COMPANIES, String.class));

    }

}
