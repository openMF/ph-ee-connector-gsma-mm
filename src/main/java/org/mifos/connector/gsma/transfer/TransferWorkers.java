package org.mifos.connector.gsma.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.ZeebeClient;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultExchange;
import org.mifos.connector.common.gsma.dto.GSMATransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;

import static org.mifos.connector.gsma.camel.config.CamelProperties.*;
import static org.mifos.connector.gsma.zeebe.ZeebeExpressionVariables.TRANSFER_RETRY_COUNT;

@Component
public class TransferWorkers {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ZeebeClient zeebeClient;

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private CamelContext camelContext;

    @Value("${zeebe.client.evenly-allocated-max-jobs}")
    private int workerMaxJobs;

    @PostConstruct
    public void setupWorkers() {

        zeebeClient.newWorker()
                .jobType("initiateTransfer")
                .handler((client, job) -> {
                    logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
                    Map<String, Object> variables = job.getVariablesAsMap();
                    variables.put(TRANSFER_RETRY_COUNT, -1);

                    Exchange exchange = new DefaultExchange(camelContext);
                    exchange.setProperty(CORRELATION_ID, variables.get("transactionId"));
                    exchange.setProperty(CHANNEL_REQUEST, variables.get("channelRequest"));
                    exchange.setProperty(GSMA_CHANNEL_REQUEST, variables.get("gsmaChannelRequest"));
                    exchange.setProperty(IS_RTP_REQUEST, variables.get(IS_RTP_REQUEST));
                    exchange.setProperty(TRANSACTION_TYPE, variables.get(TRANSACTION_TYPE));
                    exchange.setProperty(GSMA_AUTHORIZATION_CODE, variables.get(GSMA_AUTHORIZATION_CODE));

                    producerTemplate.send("direct:transfer-route", exchange);

                    client.newCompleteCommand(job.getKey())
                            .variables(variables)
                            .send()
                            .join();
                })
                .name("initiateTransfer")
                .maxJobsActive(workerMaxJobs)
                .open();

        zeebeClient.newWorker()
                .jobType("initiateIntTransfer")
                .handler((client, job) -> {
                    logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
                    Map<String, Object> variables = job.getVariablesAsMap();
                    variables.put(TRANSFER_RETRY_COUNT, -1);
                    Exchange exchange = new DefaultExchange(camelContext);
                    exchange.setProperty(CORRELATION_ID, variables.get("transactionId"));
                    exchange.setProperty(CHANNEL_REQUEST, variables.get("channelRequest"));
                    exchange.setProperty(TRANSACTION_TYPE, variables.get(TRANSACTION_TYPE));
                    exchange.setProperty(QUOTE_ID, variables.get(QUOTE_ID));
                    exchange.setProperty(QUOTE_REFERENCE, variables.get(QUOTE_REFERENCE));


                    GSMATransaction gsmaTransaction = objectMapper.readValue((String) variables.get("gsmaChannelRequest"), GSMATransaction.class);
                    gsmaTransaction.getInternationalTransferInformation().setQuoteId((String) variables.get(QUOTE_ID));
                    gsmaTransaction.getInternationalTransferInformation().setQuotationReference((String) variables.get(QUOTE_REFERENCE));

                    exchange.setProperty(GSMA_CHANNEL_REQUEST, gsmaTransaction);

                    producerTemplate.send("direct:transfer-route", exchange);

                    client.newCompleteCommand(job.getKey())
                            .variables(variables)
                            .send()
                            .join();
                })
                .name("initiateIntTransfer")
                .maxJobsActive(workerMaxJobs)
                .open();

        zeebeClient.newWorker()
                .jobType("initiatePayeeDeposit")
                .handler((client, job) -> {
                    logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());

                    Map<String, Object> variables = job.getVariablesAsMap();
                    GSMATransaction gsmaChannelRequest = objectMapper.readValue((String) variables.get("gsmaChannelRequest"), GSMATransaction.class);
                    GSMATransaction gsmaPayeeRequest = new GSMATransaction();
                    variables.put(TRANSFER_RETRY_COUNT, -1);
                    // Do currency converted values instead of original values
                    String convertedAmount = gsmaChannelRequest.getInternationalTransferInformation().getReceivingAmount();
                    String convertedCurrency = gsmaChannelRequest.getInternationalTransferInformation().getReceivingCurrency();

                    gsmaPayeeRequest.setDescriptionText("Original Amount = " + gsmaChannelRequest.getAmount() + ", currency = " + gsmaChannelRequest.getCurrency()
                           +":::: Converted Amount = " + convertedAmount + ", currency = " + convertedCurrency);
                    logger.info(gsmaPayeeRequest.getDescriptionText());
                    gsmaPayeeRequest.setAmount(convertedAmount);
                    gsmaPayeeRequest.setCurrency(convertedCurrency);
                    gsmaPayeeRequest.setRequestingOrganisationTransactionReference(variables.get("transactionId").toString());
                    gsmaPayeeRequest.setCreditParty(gsmaChannelRequest.getCreditParty());
                    gsmaPayeeRequest.setDebitParty(gsmaChannelRequest.getDebitParty());
                    gsmaPayeeRequest.setReceivingLei(gsmaChannelRequest.getReceivingLei());
                    gsmaPayeeRequest.setRequestingLei(gsmaChannelRequest.getRequestingLei());
                    gsmaPayeeRequest.setType("transfer");

                    Exchange exchange = new DefaultExchange(camelContext);
                    exchange.setProperty(GSMA_CHANNEL_REQUEST, gsmaPayeeRequest);
                    exchange.setProperty(RECEIVING_TENANT, gsmaChannelRequest.getReceivingLei());
                    exchange.setProperty(CORRELATION_ID, variables.get("transactionId"));

                    producerTemplate.send("direct:transfer-route", exchange);

                    client.newCompleteCommand(job.getKey())
                            .variables(variables)
                            .send()
                            .join();
                })
                .name("initiatePayeeDeposit")
                .maxJobsActive(workerMaxJobs)
                .open();
    }
}
