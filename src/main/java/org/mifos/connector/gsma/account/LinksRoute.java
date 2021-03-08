package org.mifos.connector.gsma.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.mifos.connector.common.channel.dto.TransactionChannelRequestDTO;
import org.mifos.connector.common.gsma.dto.ErrorDTO;
import org.mifos.connector.common.gsma.dto.GsmaParty;
import org.mifos.connector.common.gsma.dto.LinksDTO;
import org.mifos.connector.common.gsma.dto.RequestStateDTO;
import org.mifos.connector.common.mojaloop.dto.PartyIdInfo;
import org.mifos.connector.gsma.auth.AccessTokenStore;
import org.mifos.connector.gsma.transfer.CorrelationIDStore;
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
import static org.mifos.connector.gsma.zeebe.ZeebeExpressionVariables.LINK_CREATE_FAILED;

@Component
public class LinksRoute extends RouteBuilder {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccessTokenStore accessTokenStore;

    @Autowired
    private CorrelationIDStore correlationIDStore;

    @Autowired
    private LinksResponseProcessor linksResponseProcessor;

    @Value("${gsma.api.host}")
    private String BaseURL;

    @Value("${camel.host}")
    private String HostURL;

    @Value("${gsma.api.account}")
    private String account;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void configure() throws Exception {

        /**
         * Base route to create link
         */
        from("direct:links-route")
                .id("links-route")
                .log(LoggingLevel.INFO, "Creating ${exchangeProperty."+ACCOUNT_ACTION+"} for Identifier")
                .to("direct:get-access-token")
                .process(exchange -> exchange.setProperty(ACCESS_TOKEN, accessTokenStore.getAccessToken()))
                .log(LoggingLevel.INFO, "Got access token, moving on to API call.")
                .to("direct:create-link-async")
                .log(LoggingLevel.INFO, "Transaction API response: ${body}")
                .choice()
                .when(header("CamelHttpResponseCode").isEqualTo("202"))
                    .log(LoggingLevel.INFO, "Link request successful")
                    .unmarshal().json(JsonLibrary.Jackson, RequestStateDTO.class)
                    .process(exchange -> {
                        correlationIDStore.addMapping(exchange.getIn().getBody(RequestStateDTO.class).getServerCorrelationId(),
                                exchange.getProperty(CORRELATION_ID, String.class));
                        logger.info("Saved correlationId mapping");
                    })
                .otherwise()
                    .log(LoggingLevel.ERROR, "Link request unsuccessful")
                    .unmarshal().json(JsonLibrary.Jackson, ErrorDTO.class)
                    .process(exchange -> {
                        exchange.setProperty(LINK_CREATE_FAILED, true);
                        exchange.setProperty(ERROR_INFORMATION, exchange.getIn().getBody(ErrorDTO.class).getErrorDescription());
                    })
                    .process(linksResponseProcessor)
                .endChoice();


        /**
         * Async call to Links API
         */
        from("direct:create-link-async")
                .id("create-link-async")
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader("X-Date", simple(ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT )))
                .setHeader("Authorization", simple("Bearer ${exchangeProperty."+ACCESS_TOKEN+"}"))
                .setHeader("X-Callback-URL", simple(HostURL + "/links/callback"))
                .setHeader("X-CorrelationID", simple("${exchangeProperty."+ CORRELATION_ID +"}"))
                .setHeader("Content-Type", constant("application/json"))
                .setBody(exchange -> exchange.getProperty(LINKS_REQUEST_BODY))
                .marshal().json(JsonLibrary.Jackson)
                .log(LoggingLevel.INFO, "Links Request Body: ${body}")
                .toD(BaseURL + account + "/${exchangeProperty."+IDENTIFIER_TYPE+"}/${exchangeProperty."+IDENTIFIER+"}/links?bridgeEndpoint=true&throwExceptionOnFailure=false");


        /**
         * Sync call to Links API
         */
        from("direct:create-link")
                .id("create-link")
                .removeHeader("*")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader("X-Date", simple(ZonedDateTime.now( ZoneOffset.UTC ).format( DateTimeFormatter.ISO_INSTANT )))
                .setHeader("Authorization", simple("Bearer ${exchangeProperty."+ACCESS_TOKEN+"}"))
                .setHeader("Content-Type", constant("application/json"))
                .setBody(exchange -> exchange.getProperty(LINKS_REQUEST_BODY))
                .marshal().json(JsonLibrary.Jackson)
                .log(LoggingLevel.INFO, "Links Request Body: ${body}")
                .toD(BaseURL + account + "/${exchangeProperty."+IDENTIFIER_TYPE+"}/${exchangeProperty."+IDENTIFIER+"}/links?bridgeEndpoint=true&throwExceptionOnFailure=false");


        /**
         * Links API callback
         */
        from("rest:PUT:/links/callback")
                .log(LoggingLevel.INFO, "Callback body ${body}")
                .unmarshal().json(JsonLibrary.Jackson, RequestStateDTO.class)
                .process(exchange -> {
                    String serverUUID = exchange.getIn().getBody(RequestStateDTO.class).getServerCorrelationId();
                    exchange.setProperty(CORRELATION_ID, correlationIDStore.getClientCorrelation(serverUUID));
                })
                .choice()
                .when(exchange -> exchange.getIn().getBody(RequestStateDTO.class).getStatus().equals("completed"))
                    .setProperty(LINK_CREATE_FAILED, constant(false))
                    .setProperty(RESPONSE_OBJECT_TYPE, constant("links"))
                    .to("direct:response-route")
                    .choice()
                    .when(exchange -> exchange.getProperty(RESPONSE_OBJECT_AVAILABLE, Boolean.class))
                        .process(exchange -> {
                            String linkref = exchange.getProperty(RESPONSE_OBJECT, LinksDTO.class).getLinkReference();
                            exchange.setProperty(LINK_REFERENCE, linkref);
                        })
                        .log(LoggingLevel.INFO, "Link reference is: ${exchangeProperty."+LINK_REFERENCE+"}")
                    .otherwise()
                        .setProperty(ERROR_INFORMATION, simple("Error in getting link reference."))
                        .setProperty(LINK_CREATE_FAILED, constant(true))
                    .endChoice()
                .otherwise()
                    .process(exchange -> exchange.setProperty(ERROR_INFORMATION, exchange.getIn().getBody(RequestStateDTO.class).getError().getErrorDescription()))
                    .setProperty(LINK_CREATE_FAILED, constant(true))
                .end()
                .process(linksResponseProcessor);


        /**
         * API to start base route
         */
        from("rest:POST:/links")
                .log(LoggingLevel.INFO, "Got Links POST request")
                .process(exchange -> {
                    exchange.setProperty(CHANNEL_REQUEST, exchange.getIn().getBody());
                    exchange.setProperty(CORRELATION_ID, UUID.randomUUID());
                    exchange.setProperty(IS_RTP_REQUEST, false);

                    TransactionChannelRequestDTO channelRequest = objectMapper.readValue(exchange.getProperty(CHANNEL_REQUEST, String.class), TransactionChannelRequestDTO.class);
                    GsmaParty party = new GsmaParty();
                    LinksDTO linksDTO = new LinksDTO();

                    PartyIdInfo sourceParty = exchange.getProperty(IS_RTP_REQUEST, Boolean.class) ? channelRequest.getPayer().getPartyIdInfo() : channelRequest.getPayee().getPartyIdInfo();
                    PartyIdInfo destinationParty = exchange.getProperty(IS_RTP_REQUEST, Boolean.class) ? channelRequest.getPayee().getPartyIdInfo() : channelRequest.getPayer().getPartyIdInfo();

                    party.setKey(sourceParty.getPartyIdType().toString().toLowerCase());
                    party.setValue(sourceParty.getPartyIdentifier());

                    GsmaParty[] sourceAccounts = new GsmaParty[]{ party };
                    linksDTO.setStatus("active");
                    linksDTO.setMode("both");
                    linksDTO.setSourceAccountIdentifiers(sourceAccounts);

                    exchange.setProperty(LINKS_REQUEST_BODY, linksDTO);
                    exchange.setProperty(IDENTIFIER_TYPE, destinationParty.getPartyIdType().toString().toLowerCase());
                    exchange.setProperty(IDENTIFIER, destinationParty.getPartyIdentifier());
                })
                .to("direct:links-route");

    }
}
