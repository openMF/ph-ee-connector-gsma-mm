package org.mifos.connector.gsma.zeebe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;


@Component
public class ZeebeeWorkers {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @PostConstruct
    public void setupWorkers() {

    }
}