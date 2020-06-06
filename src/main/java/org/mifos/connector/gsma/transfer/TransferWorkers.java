package org.mifos.connector.gsma.transfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class TransferWorkers {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @PostConstruct
    public void setupWorkers() {

    }
}
