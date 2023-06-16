package org.mifos.connector.gsma.account;

import static org.mifos.connector.gsma.camel.config.CamelProperties.CHANNEL_REQUEST;
import static org.mifos.connector.gsma.camel.config.CamelProperties.CORRELATION_ID;
import static org.mifos.connector.gsma.camel.config.CamelProperties.IDENTIFIER;
import static org.mifos.connector.gsma.camel.config.CamelProperties.IDENTIFIER_TYPE;
import static org.mifos.connector.gsma.camel.config.CamelProperties.IS_RTP_REQUEST;
import static org.mifos.connector.gsma.camel.config.CamelProperties.LINKS_REQUEST_BODY;
import static org.mifos.connector.gsma.camel.config.CamelProperties.TRANSACTION_CORRELATION_ID;
import static org.mifos.connector.gsma.zeebe.ZeebeExpressionVariables.LINK_CREATION_COUNT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import java.util.Map;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultExchange;
import org.mifos.connector.common.channel.dto.TransactionChannelRequestDTO;
import org.mifos.connector.common.gsma.dto.GsmaParty;
import org.mifos.connector.common.gsma.dto.LinksDTO;
import org.mifos.connector.common.mojaloop.dto.PartyIdInfo;
import org.mifos.connector.gsma.transfer.CorrelationIDStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LinksWorkers {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ZeebeClient zeebeClient;

    @Value("${zeebe.client.evenly-allocated-max-jobs}")
    private int workerMaxJobs;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CorrelationIDStore correlationIDStore;

    @PostConstruct
    public void setupWorkers() {

        zeebeClient.newWorker().jobType("createAccountLink").handler((client, job) -> {
            logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
            Map<String, Object> variables = job.getVariablesAsMap();
            variables.put(LINK_CREATION_COUNT, 1 + (Integer) variables.getOrDefault(LINK_CREATION_COUNT, -1));

            Exchange exchange = new DefaultExchange(camelContext);
            exchange.setProperty(CORRELATION_ID, UUID.randomUUID());
            exchange.setProperty(TRANSACTION_CORRELATION_ID, variables.get("transactionId"));
            exchange.setProperty(CHANNEL_REQUEST, variables.get("channelRequest"));
            exchange.setProperty(IS_RTP_REQUEST, variables.get(IS_RTP_REQUEST));

            correlationIDStore.addMapping(exchange.getProperty(CORRELATION_ID, String.class),
                    exchange.getProperty(TRANSACTION_CORRELATION_ID, String.class));

            TransactionChannelRequestDTO channelRequest = objectMapper.readValue(exchange.getProperty(CHANNEL_REQUEST, String.class),
                    TransactionChannelRequestDTO.class);
            GsmaParty party = new GsmaParty();
            LinksDTO linksDTO = new LinksDTO();

            PartyIdInfo sourceParty = exchange.getProperty(IS_RTP_REQUEST, Boolean.class) ? channelRequest.getPayer().getPartyIdInfo()
                    : channelRequest.getPayee().getPartyIdInfo();
            PartyIdInfo destinationParty = exchange.getProperty(IS_RTP_REQUEST, Boolean.class) ? channelRequest.getPayee().getPartyIdInfo()
                    : channelRequest.getPayer().getPartyIdInfo();

            party.setKey(sourceParty.getPartyIdType().toString().toLowerCase());
            party.setValue(sourceParty.getPartyIdentifier());

            GsmaParty[] sourceAccounts = new GsmaParty[] { party };
            linksDTO.setStatus("active");
            linksDTO.setMode("both");
            linksDTO.setSourceAccountIdentifiers(sourceAccounts);

            exchange.setProperty(LINKS_REQUEST_BODY, linksDTO);

            exchange.setProperty(IDENTIFIER_TYPE, destinationParty.getPartyIdType().toString().toLowerCase());
            exchange.setProperty(IDENTIFIER, destinationParty.getPartyIdentifier());

            producerTemplate.send("direct:links-route", exchange);

            client.newCompleteCommand(job.getKey()).variables(variables).send().join();
        }).name("createAccountLink").maxJobsActive(workerMaxJobs).open();

    }
}
