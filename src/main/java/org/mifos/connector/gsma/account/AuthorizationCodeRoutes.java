package org.mifos.connector.gsma.account;

import static org.mifos.connector.gsma.camel.config.CamelProperties.ACCESS_TOKEN;
import static org.mifos.connector.gsma.camel.config.CamelProperties.ACCOUNT_ACTION;
import static org.mifos.connector.gsma.camel.config.CamelProperties.IDENTIFIER;
import static org.mifos.connector.gsma.camel.config.CamelProperties.IDENTIFIER_TYPE;
import static org.mifos.connector.gsma.camel.config.CamelProperties.IS_API_CALL;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.mifos.connector.common.gsma.dto.AuthorizationCodeDTO;
import org.mifos.connector.gsma.auth.AccessTokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationCodeRoutes extends RouteBuilder {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccessTokenStore accessTokenStore;

    @Value("${gsma.api.host}")
    private String baseURL;

    @Value("${gsma.api.account}")
    private String account;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void configure() throws Exception {

        /**
         * BAse route for Authorization Code
         */
        from("direct:authorization-code-route").id("authorization-code-route")
                .log(LoggingLevel.INFO, "Getting ${exchangeProperty." + ACCOUNT_ACTION + "} for Identifier").to("direct:get-access-token")
                .process(exchange -> exchange.setProperty(ACCESS_TOKEN, accessTokenStore.getAccessToken()))
                .log(LoggingLevel.INFO, "Got access token, moving on to API call.").process(exchange -> {
                    AuthorizationCodeDTO authorizationCodeDTO = new AuthorizationCodeDTO();
                    authorizationCodeDTO.setRequestDate(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));

                    exchange.getIn().setBody(authorizationCodeDTO);
                }).to("direct:get-authorization-code").choice().when(header("CamelHttpResponseCode").isEqualTo("201"))
                .log(LoggingLevel.INFO, "Successful in creating authorization object: ${body}").process(exchange -> {
                    AuthorizationCodeDTO authorizationCodeResponse = objectMapper.readValue(exchange.getIn().getBody(String.class),
                            AuthorizationCodeDTO.class);
                    exchange.getIn().setBody(authorizationCodeResponse.getAuthorisationCode());
                }).otherwise().log(LoggingLevel.INFO, "Error in getting Authorization Code").endChoice();

        /**
         * POST call to GSMA
         */
        from("direct:get-authorization-code").id("get-authorization-code").log(LoggingLevel.INFO, "Fetching authorization code")
                .removeHeader("*").setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader("X-Date", simple(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)))
                .setHeader("Authorization", simple("Bearer ${exchangeProperty." + ACCESS_TOKEN + "}")).marshal().json(JsonLibrary.Jackson)
                .toD(baseURL + account + "/${exchangeProperty." + IDENTIFIER_TYPE + "}/${exchangeProperty." + IDENTIFIER
                        + "}/authorisationcodes?bridgeEndpoint=true&throwExceptionOnFailure=false");

        /**
         * API to get Authorization Code
         */
        from("rest:GET:/account/authcode/{identifier_type}/{identifier}").log(LoggingLevel.INFO, "Getting Authorization Code")
                .process(exchange -> {
                    exchange.setProperty(IDENTIFIER_TYPE, exchange.getIn().getHeader("identifier_type"));
                    exchange.setProperty(IDENTIFIER, exchange.getIn().getHeader("identifier"));
                    exchange.setProperty(IS_API_CALL, "true");
                }).to("direct:authorization-code-route");
    }
}
