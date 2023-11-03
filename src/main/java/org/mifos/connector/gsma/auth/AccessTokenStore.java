package org.mifos.connector.gsma.auth;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AccessTokenStore {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    public String accessToken;
    public LocalDateTime expiresOn;

    public AccessTokenStore() {
        this.expiresOn = LocalDateTime.now();
        logger.info("ACCESS TOKEN STORE CREATED!");
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

    public boolean isValid(LocalDateTime dateTime) {
        if (dateTime.isBefore(expiresOn)) {
            return true;
        } else {
            return false;
        }
    }

}
