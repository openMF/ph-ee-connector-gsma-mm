package org.mifos.connector.gsma.transfer;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@Component
@Getter
@Setter
public class CorrelationIDStore {
    HashMap<String, String> correlation = new HashMap<>();

    public void addMapping(String serverCorrelation, String clientCorrelation) {
        correlation.put(serverCorrelation, clientCorrelation);
    }

    public String getClientCorrelation (String serverCorrelation) {
        return correlation.get(serverCorrelation);
    }

    public Stream<String> getServerCorrelations(String clientCorrelation) {
        return correlation
                .entrySet()
                .stream()
                .filter(entry -> clientCorrelation.equals(entry.getValue()))
                .map(Map.Entry::getKey);
    }

    public boolean isClientCorrelationPresent (String clientCorrelation) {
        return correlation.containsValue(clientCorrelation);
    }
}
