package org.mifos.connector.gsma.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.ZeebeClient;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultExchange;
import org.mifos.connector.common.channel.dto.TransactionChannelRequestDTO;
import org.mifos.connector.common.mojaloop.dto.PartyIdInfo;
import org.mifos.connector.gsma.account.dto.LinksDTO;
import org.mifos.connector.gsma.transfer.dto.Party;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;

import static org.mifos.connector.gsma.camel.config.CamelProperties.*;
import static org.mifos.connector.gsma.zeebe.ZeebeExpressionVariables.PAYEE_LOOKUP_RETRY_COUNT;

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

    @PostConstruct
    public void setupWorkers() {

        zeebeClient.newWorker()
                .jobType("createAccountLink")
                .handler((client, job) -> {
                    logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
                    Map<String, Object> variables = job.getVariablesAsMap();
//                    variables.put(PAYEE_LOOKUP_RETRY_COUNT, 1 + (Integer) variables.getOrDefault(PAYEE_LOOKUP_RETRY_COUNT, -1));

                    Exchange exchange = new DefaultExchange(camelContext);
                    exchange.setProperty(CORRELATION_ID, variables.get("transactionId"));
                    exchange.setProperty(CHANNEL_REQUEST, variables.get("channelRequest"));
                    exchange.setProperty(IS_RTP_REQUEST, variables.get(IS_RTP_REQUEST));

                    TransactionChannelRequestDTO channelRequest = objectMapper.readValue(exchange.getProperty(CHANNEL_REQUEST, String.class), TransactionChannelRequestDTO.class);
                    Party party = new Party();
                    LinksDTO linksDTO = new LinksDTO();

                    PartyIdInfo sourceParty = exchange.getProperty(IS_RTP_REQUEST, Boolean.class) ? channelRequest.getPayer().getPartyIdInfo() : channelRequest.getPayee().getPartyIdInfo();
                    PartyIdInfo destinationParty = exchange.getProperty(IS_RTP_REQUEST, Boolean.class) ? channelRequest.getPayee().getPartyIdInfo() : channelRequest.getPayer().getPartyIdInfo();

                    party.setKey(sourceParty.getPartyIdType().toString().toLowerCase());
                    party.setValue(sourceParty.getPartyIdentifier());

                    Party[] sourceAccounts = new Party[]{ party };
                    linksDTO.setStatus("active");
                    linksDTO.setMode("both");
                    linksDTO.setSourceAccountIdentifiers(sourceAccounts);

                    exchange.setProperty(LINKS_REQUEST_BODY, linksDTO);

                    exchange.setProperty(IDENTIFIER_TYPE, destinationParty.getPartyIdType().toString().toLowerCase());
                    exchange.setProperty(IDENTIFIER, destinationParty.getPartyIdentifier());

                    producerTemplate.send("direct:links-route", exchange);

                    client.newCompleteCommand(job.getKey())
                            .variables(variables)
                            .send()
                            .join();
                })
                .name("createAccountLink")
                .maxJobsActive(workerMaxJobs)
                .open();

    }
}
