package org.mifos.connector.gsma.auth;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.mifos.connector.gsma.auth.dto.AccessTokenDTO;
import org.mifos.connector.gsma.auth.dto.AccessTokenStore;
import org.mifos.connector.gsma.auth.dto.ErrorDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.Base64;

@Component
public class AuthRoutes extends RouteBuilder {

    @Value("${gsma.auth.host}")
    private String authUrl;

    @Value("${gsma.auth.client-key}")
    private String clientKey;

    @Value("${gsma.auth.client-secret}")
    private String clientSecret;

    @Autowired
    private AccessTokenStore accessTokenStore;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void configure() {

        /**
         * Error handling route
         */
        from("direct:access-token-error")
                .id("access-token-error")
                .unmarshal().json(JsonLibrary.Jackson, ErrorDTO.class)
                .process(exchange -> {
                    logger.error(exchange.getIn().getBody(ErrorDTO.class).getErrorMessage());
                    // TODO: Improve Error Handling
                });

        /**
         * Save Access Token to AccessTokenStore
         */
        from("direct:access-token-save")
                .id("access-token-save")
                .unmarshal().json(JsonLibrary.Jackson, AccessTokenDTO.class)
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        accessTokenStore.setAccessToken(exchange.getIn().getBody(AccessTokenDTO.class).getAccess_token());
                        accessTokenStore.setExpiresOn(exchange.getIn().getBody(AccessTokenDTO.class).getExpires_in());
                        logger.info("Saved Access Token: " + accessTokenStore.getAccessToken());
                    }
                });

        /**
         * Fetch Access Token from GSMA API
         */
        from("direct:access-token-fetch")
                .id("access-token-fetch")
                .log(LoggingLevel.INFO, "Fetching access token")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader("Authorization", simple("Basic " + createAuthHeader(clientKey, clientSecret)))
                .setHeader("Content-Type", constant("application/x-www-form-urlencoded"))
                .removeHeader(Exchange.HTTP_PATH)
                .setBody(simple("grant_type=client_credentials"))
                .toD(authUrl + "?bridgeEndpoint=true");

        /**
         * Access Token check validity and return value
         */
        from("direct:get-access-token")
                .id("get-access-token")
                .choice()
                    .when(exchange -> accessTokenStore.isValid(LocalDateTime.now()))
                        .log("Access token valid. Continuing.")
                    .otherwise()
                        .log("Access token expired or not present")
                        .to("direct:access-token-fetch")
                        .choice()
                            .when(header("CamelHttpResponseCode").isEqualTo("200"))
                            .log("Access Token Fetch Successful")
                            .to("direct:access-token-save")
                        .otherwise()
                            .log("Access Token Fetch Unsuccessful")
                            .to("direct:access-token-error");
    }

    private String createAuthHeader(String key, String secret) {
        String encodedAuth = null;
        try {
            encodedAuth = Base64.getEncoder().encodeToString((key + ":" + secret).getBytes("utf-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return encodedAuth;
    }
}
