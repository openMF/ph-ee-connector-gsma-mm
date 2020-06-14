package org.mifos.connector.gsma.transfer;

import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
public class CorrelationIDStore {
    HashMap<String, String> correlation = new HashMap<>();

    public HashMap<String, String> getCorrelation() {
        return correlation;
    }

    public void setCorrelation(HashMap<String, String> correlation) {
        this.correlation = correlation;
    }

    public void addMapping(String serverCorrelation, String clientCorrelation) {
        correlation.put(serverCorrelation, clientCorrelation);
    }

    public String getClientCorrelation (String serverCorrelation) {
        return correlation.get(serverCorrelation);
    }
}
