package org.mifos.connector.gsma.auth;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.mifos.connector.gsma.auth.dto.AccessTokenDTO;
import org.mifos.connector.gsma.auth.dto.ErrorDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Stack;

@Component
public class AuthRoutes extends RouteBuilder {

    @Value("${gsma.auth.host}")
    private String authUrl;

    @Value("${gsma.auth.client-key}")
    private String clientKey;

    @Value("${gsma.auth.client-secret}")
    private String clientSecret;

    private AuthProcessor authProcessor;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public void configure() {

        from("direct:access-token-error")
                .id("access-token-error")
                .unmarshal().json(JsonLibrary.Jackson, ErrorDTO.class)
                .process(exchange -> {
                    logger.error(exchange.getIn().getBody(ErrorDTO.class).getErrorMessage());
                    // TODO: Improve Error Handling
                });

        from("direct:access-token-save")
                .id("access-token-save")
                .unmarshal().json(JsonLibrary.Jackson, AccessTokenDTO.class)
                .process(exchange -> {
                    LocalDateTime expiryDateTime = LocalDateTime.now().plusSeconds(exchange.getIn().getBody(AccessTokenDTO.class).getExpires_in());
                    DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
                    exchange.setProperty("expiry", expiryDateTime.format(formatter));
                    exchange.setProperty("accessToken", exchange.getIn().getBody(AccessTokenDTO.class).getAccess_token());
                })
                .log("Access token added to property");

        from("direct:access-token-fetch")
                .id("access-token-fetch")
                .log(LoggingLevel.INFO, "Fetching access token")
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .setHeader("Authorization", simple("Basic " + createAuthHeader(clientKey, clientSecret)))
                .setHeader("Content-Type", constant("application/x-www-form-urlencoded"))
                .removeHeader(Exchange.HTTP_PATH)
                .setBody(simple("grant_type=client_credentials"))
                .toD(authUrl + "?bridgeEndpoint=true");

        from("direct:get-access-token")
                .id("get-access-token")
                .choice()
                    .when(exchange -> {
                        String expiry = exchange.getProperty("expiry", String.class);
                        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
                        if (expiry == null || LocalDateTime.now().isAfter(LocalDateTime.parse(expiry, formatter)))
                            return true;
                        return false;
                    })
                    .log("Access token expired or not present")
                    .to("direct:access-token-fetch")
                    .choice()
                        .when(header("CamelHttpResponseCode").isEqualTo("200"))
                        .log("Access Token Fetch Successful")
                        .to("direct:access-token-save")
                    .otherwise()
                        .log("Access Token Fetch Unsuccessful")
                        .to("direct:access-token-error")
                .log("Hopefully accessToken: ");
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

    @Component
    class AccessToken {
        public String accessToken;
        public LocalDateTime expiresOn;

        public AccessToken() {
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public LocalDateTime getExpiresOn() {
            return expiresOn;
        }

        public void setExpiresOn(int expires_in) {
            this.expiresOn = LocalDateTime.now().plusSeconds(expires_in);
        }
    }

}
