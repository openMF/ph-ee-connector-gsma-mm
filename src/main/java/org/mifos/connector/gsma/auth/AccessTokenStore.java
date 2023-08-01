package org.mifos.connector.gsma.auth;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Getter
@Setter
public class AccessTokenStore {
    private String accessToken;
    private LocalDateTime expiresOn;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public AccessTokenStore() {
        this.expiresOn = LocalDateTime.now();
        logger.info("ACCESS TOKEN STORE CREATED!");
    }

    public void setExpiresOn(int expires_in) {
        this.expiresOn = LocalDateTime.now().plusSeconds(expires_in);
    }

    public boolean isValid(LocalDateTime dateTime) {
        if (dateTime.isBefore(expiresOn))
            return true;
        else
            return false;
    }
}