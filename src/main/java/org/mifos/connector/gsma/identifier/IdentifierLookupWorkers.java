package org.mifos.connector.gsma.identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class IdentifierLookupWorkers {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @PostConstruct
    public void setupWorkers() {

    }
}
