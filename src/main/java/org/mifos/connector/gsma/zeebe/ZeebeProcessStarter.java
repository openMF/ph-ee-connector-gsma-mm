package org.mifos.connector.gsma.zeebe;

import io.zeebe.client.ZeebeClient;
import org.apache.camel.Exchange;
import org.mifos.connector.gsma.camel.config.CamelProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;


@Component
public class ZeebeProcessStarter {

    private static final Logger logger = LoggerFactory.getLogger(ZeebeProcessStarter.class);

    @Autowired
    private ZeebeClient zeebeClient;

    public static void zeebeVariablesToCamelHeaders(Map<String, Object> variables, Exchange exchange, String... names) {
        for (String name : names) {
            Object value = variables.get(name);
            if (value == null) {
                logger.error("failed to find Zeebe variable name {}", name);
            }
            exchange.getIn().setHeader(name, value);
        }
    }

    public static void camelHeadersToZeebeVariables(Exchange exchange, Map<String, Object> variables, String... names) {
        for (String name : names) {
            String header = exchange.getIn().getHeader(name, String.class);
            if (header == null) {
                logger.error("failed to find Camel Exchange header {}", name);
            }
            variables.put(name, header);
        }
    }

    public void startZeebeWorkflow(String workflowId, Map<String, Object> extraVariables) {
        Map<String, Object> variables = new HashMap<>();
        variables.putAll(extraVariables);
        // TODO: Add extra variables if required. Such as origin date.

        zeebeClient.newCreateInstanceCommand()
                .bpmnProcessId(workflowId)
                .latestVersion() // .version(1)
                .variables(variables)
                .send()
                .join();

        logger.info("zeebee workflow instance from process {} started", workflowId);
    }
}
