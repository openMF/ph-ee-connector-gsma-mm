package org.mifos.connector.gsma.account;

import static org.mifos.connector.gsma.camel.config.CamelProperties.CHANNEL_REQUEST;
import static org.mifos.connector.gsma.camel.config.CamelProperties.DEBIT_MANDATE_BODY;
import static org.mifos.connector.gsma.camel.config.CamelProperties.ERROR_INFORMATION;
import static org.mifos.connector.gsma.camel.config.CamelProperties.IDENTIFIER;
import static org.mifos.connector.gsma.camel.config.CamelProperties.IDENTIFIER_TYPE;
import static org.mifos.connector.gsma.camel.config.CamelProperties.MANDATE_CREATE_FAILED;
import static org.mifos.connector.gsma.camel.config.CamelProperties.MANDATE_REFERENCE;
import static org.mifos.connector.gsma.zeebe.ZeebeExpressionVariables.NUMBER_OF_PAYMENTS;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultExchange;
import org.mifos.connector.common.channel.dto.TransactionChannelRequestDTO;
import org.mifos.connector.common.gsma.dto.DebitMandateDTO;
import org.mifos.connector.common.mojaloop.dto.PartyIdInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DebitMandateWorkers {

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

        zeebeClient.newWorker().jobType("createDebitMandate").handler((client, job) -> {
            logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
            Map<String, Object> variables = job.getVariablesAsMap();

            Exchange exchange = new DefaultExchange(camelContext);
            exchange.setProperty(CHANNEL_REQUEST, variables.get("channelRequest"));

            TransactionChannelRequestDTO channelRequest = objectMapper.readValue(exchange.getProperty(CHANNEL_REQUEST, String.class),
                    TransactionChannelRequestDTO.class);
            PartyIdInfo payerParty = channelRequest.getPayer().getPartyIdInfo();
            exchange.setProperty(IDENTIFIER_TYPE, payerParty.getPartyIdType().toString().toLowerCase());
            exchange.setProperty(IDENTIFIER, payerParty.getPartyIdentifier());

            DebitMandateDTO debitMandateRequest = new DebitMandateDTO();

            debitMandateRequest.setRequestDate(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
            debitMandateRequest.setStartDate(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
            debitMandateRequest.setAmountLimit(channelRequest.getAmount().getAmount());
            debitMandateRequest.setCurrency(channelRequest.getAmount().getCurrency());
            debitMandateRequest.setNumberOfPayments((Integer) variables.getOrDefault(NUMBER_OF_PAYMENTS, 10));

            exchange.setProperty(DEBIT_MANDATE_BODY, debitMandateRequest);

            producerTemplate.send("direct:debit-mandate-base", exchange);

            variables.put(MANDATE_CREATE_FAILED, exchange.getProperty(MANDATE_CREATE_FAILED, Boolean.class));

            if (exchange.getProperty(MANDATE_CREATE_FAILED, Boolean.class)) {
                variables.put(ERROR_INFORMATION, exchange.getProperty(ERROR_INFORMATION, String.class));
            } else {
                variables.put(MANDATE_REFERENCE, exchange.getProperty(MANDATE_REFERENCE, String.class));
            }

            client.newCompleteCommand(job.getKey()).variables(variables).send().join();
        }).name("createDebitMandate").maxJobsActive(workerMaxJobs).open();

    }
}
