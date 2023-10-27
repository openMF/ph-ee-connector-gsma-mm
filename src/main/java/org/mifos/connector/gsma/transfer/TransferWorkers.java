package org.mifos.connector.gsma.transfer;

import static org.mifos.connector.gsma.camel.config.CamelProperties.CHANNEL_REQUEST;
import static org.mifos.connector.gsma.camel.config.CamelProperties.CORRELATION_ID;
import static org.mifos.connector.gsma.camel.config.CamelProperties.CURRENCY_PAIR;
import static org.mifos.connector.gsma.camel.config.CamelProperties.CURRENCY_PAIR_RATE;
import static org.mifos.connector.gsma.camel.config.CamelProperties.GSMA_AUTHORIZATION_CODE;
import static org.mifos.connector.gsma.camel.config.CamelProperties.GSMA_CHANNEL_REQUEST;
import static org.mifos.connector.gsma.camel.config.CamelProperties.IS_RTP_REQUEST;
import static org.mifos.connector.gsma.camel.config.CamelProperties.QUOTE_ID;
import static org.mifos.connector.gsma.camel.config.CamelProperties.QUOTE_REFERENCE;
import static org.mifos.connector.gsma.camel.config.CamelProperties.RECEIVING_AMOUNT;
import static org.mifos.connector.gsma.camel.config.CamelProperties.RECEIVING_CURRENCY;
import static org.mifos.connector.gsma.camel.config.CamelProperties.RECEIVING_TENANT;
import static org.mifos.connector.gsma.camel.config.CamelProperties.SENDER_AMOUNT;
import static org.mifos.connector.gsma.camel.config.CamelProperties.SENDER_CURRENCY;
import static org.mifos.connector.gsma.camel.config.CamelProperties.TRANSACTION_TYPE;
import static org.mifos.connector.gsma.zeebe.ZeebeExpressionVariables.TRANSACTION_FAILED;
import static org.mifos.connector.gsma.zeebe.ZeebeExpressionVariables.TRANSFER_RETRY_COUNT;
import static org.mifos.connector.gsma.zeebe.ZeebeMessages.TRANSFER_RESPONSE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import java.time.Duration;
import java.util.Map;
import javax.annotation.PostConstruct;
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

        zeebeClient.newWorker().jobType("initiateTransfer").handler((client, job) -> {
            logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
            Map<String, Object> variables = job.getVariablesAsMap();
            variables.put(TRANSFER_RETRY_COUNT, -1);

            Exchange exchange = new DefaultExchange(camelContext);
            exchange.setProperty(CORRELATION_ID, variables.get("transactionId"));
            exchange.setProperty(CHANNEL_REQUEST, variables.get("channelRequest"));
            exchange.setProperty(GSMA_CHANNEL_REQUEST, variables.get("gsmaChannelRequest"));
            exchange.setProperty(IS_RTP_REQUEST, variables.get(IS_RTP_REQUEST));
            exchange.setProperty(TRANSACTION_TYPE, variables.get(TRANSACTION_TYPE));
            exchange.setProperty(RECEIVING_TENANT, variables.get("tenantId"));
            exchange.setProperty("payeeTenantId", variables.get("payeeTenantId"));
            exchange.setProperty(GSMA_AUTHORIZATION_CODE, variables.get(GSMA_AUTHORIZATION_CODE));
            logger.info("Payee Tenant ID: {}", variables.get("payeeTenantId"));
            producerTemplate.send("direct:transfer-route", exchange);
            variables.put(TRANSACTION_FAILED, false);
            // added to pass the transaction request api not found error
            variables.put("payeeTenantId", exchange.getProperty("payeeTenantId"));
            zeebeClient.newPublishMessageCommand().messageName(TRANSFER_RESPONSE)
                    .correlationKey(exchange.getProperty(CORRELATION_ID, String.class)).timeToLive(Duration.ofMillis(30000))
                    .variables(variables).send();
            client.newCompleteCommand(job.getKey()).variables(variables).send().join();
        }).name("initiateTransfer").maxJobsActive(workerMaxJobs).open();

        zeebeClient.newWorker().jobType("initiateIntTransfer").handler((client, job) -> {
            logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
            Map<String, Object> variables = job.getVariablesAsMap();
            variables.put(TRANSFER_RETRY_COUNT, -1);
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.setProperty(CORRELATION_ID, variables.get("transactionId"));
            exchange.setProperty(CHANNEL_REQUEST, variables.get("channelRequest"));
            exchange.setProperty(TRANSACTION_TYPE, variables.get(TRANSACTION_TYPE));
            exchange.setProperty(QUOTE_ID, variables.get(QUOTE_ID));
            exchange.setProperty(QUOTE_REFERENCE, variables.get(QUOTE_REFERENCE));
            exchange.setProperty("payeeTenantId", variables.get("payeeTenantId"));

            GSMATransaction gsmaTransaction = objectMapper.readValue((String) variables.get("gsmaChannelRequest"), GSMATransaction.class);
            gsmaTransaction.getInternationalTransferInformation().setQuoteId((String) variables.get(QUOTE_ID));
            gsmaTransaction.getInternationalTransferInformation().setQuotationReference((String) variables.get(QUOTE_REFERENCE));

            exchange.setProperty(GSMA_CHANNEL_REQUEST, gsmaTransaction);

            producerTemplate.send("direct:transfer-route", exchange);
            variables.put("payeeTenantId", exchange.getProperty("payeeTenantId"));
            client.newCompleteCommand(job.getKey()).variables(variables).send().join();
        }).name("initiateIntTransfer").maxJobsActive(workerMaxJobs).open();

        zeebeClient.newWorker().jobType("initiatePayeeDeposit").handler((client, job) -> {
            logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());

            Map<String, Object> variables = job.getVariablesAsMap();
            GSMATransaction gsmaChannelRequest = objectMapper.readValue((String) variables.get("gsmaChannelRequest"),
                    GSMATransaction.class);
            GSMATransaction gsmaPayeeRequest = new GSMATransaction();
            variables.put(TRANSFER_RETRY_COUNT, -1);
            // Do currency converted values instead of original values
            String convertedAmount = gsmaChannelRequest.getInternationalTransferInformation().getReceivingAmount();
            String convertedCurrency = gsmaChannelRequest.getInternationalTransferInformation().getReceivingCurrency();

            gsmaPayeeRequest.setDescriptionText(
                    "Original Amount = " + gsmaChannelRequest.getAmount() + ", currency = " + gsmaChannelRequest.getCurrency()
                            + ":::: Converted Amount = " + convertedAmount + ", currency = " + convertedCurrency);
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

            variables.put(RECEIVING_AMOUNT, convertedAmount);
            variables.put(RECEIVING_CURRENCY, convertedCurrency);
            variables.put(SENDER_AMOUNT, gsmaChannelRequest.getAmount());
            variables.put(SENDER_CURRENCY, gsmaChannelRequest.getCurrency());
            variables.put(CURRENCY_PAIR, gsmaChannelRequest.getInternationalTransferInformation().getCurrencyPair());
            variables.put(CURRENCY_PAIR_RATE, gsmaChannelRequest.getInternationalTransferInformation().getCurrencyPairRate());

            producerTemplate.send("direct:transfer-route", exchange);

            client.newCompleteCommand(job.getKey()).variables(variables).send().join();
        }).name("initiatePayeeDeposit").maxJobsActive(workerMaxJobs).open();
    }
}
