package org.mifos.connector.gsma.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.ZeebeClient;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultExchange;
import org.mifos.connector.common.channel.dto.TransactionChannelRequestDTO;
import org.mifos.connector.common.gsma.dto.Bill;
import org.mifos.connector.common.gsma.dto.BillPaymentDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;

import static org.mifos.connector.gsma.camel.config.CamelProperties.*;
import static org.mifos.connector.gsma.zeebe.ZeebeExpressionVariables.BILLS_STATUS_ERROR;

@Component
public class BillsWorkers {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ZeebeClient zeebeClient;

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${zeebe.client.evenly-allocated-max-jobs}")
    private int workerMaxJobs;

    @PostConstruct
    public void setupWorkers() {

        zeebeClient.newWorker()
                .jobType("billsPayment")
                .handler((client, job) -> {
                    logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
                    Map<String, Object> variables = job.getVariablesAsMap();

                    Exchange exchange = new DefaultExchange(camelContext);
                    exchange.setProperty(CORRELATION_ID, variables.get("transactionId"));
                    exchange.setProperty(CHANNEL_REQUEST, variables.get("channelRequest"));

                    exchange.setProperty(BILLS_ACTION, "payment");

                    TransactionChannelRequestDTO channelRequest = objectMapper.readValue(exchange.getProperty(CHANNEL_REQUEST, String.class), TransactionChannelRequestDTO.class);
                    exchange.setProperty(BILL_REFERENCE, channelRequest.getPayee().getPartyIdInfo().getPartyIdentifier());
                    exchange.setProperty(IDENTIFIER_TYPE, channelRequest.getPayer().getPartyIdInfo().getPartyIdType());
                    exchange.setProperty(IDENTIFIER, channelRequest.getPayer().getPartyIdInfo().getPartyIdentifier());

                    BillPaymentDTO paymentDTO = new BillPaymentDTO();

                    paymentDTO.setAmountPaid(channelRequest.getAmount().getAmount());
                    paymentDTO.setCurrency(channelRequest.getAmount().getCurrency());

                    exchange.setProperty(BILLS_REQUEST_BODY, paymentDTO);

                    producerTemplate.send("direct:bills-route-base", exchange);

                    client.newCompleteCommand(job.getKey())
                            .variables(variables)
                            .send()
                            .join();
                })
                .name("billsPayment")
                .maxJobsActive(workerMaxJobs)
                .open();

        zeebeClient.newWorker()
                .jobType("validateBill")
                .handler((client, job) -> {
                    logger.info("Job '{}' started from process '{}' with key {}", job.getType(), job.getBpmnProcessId(), job.getKey());
                    Map<String, Object> variables = job.getVariablesAsMap();

                    Exchange exchange = new DefaultExchange(camelContext);
                    exchange.setProperty(CORRELATION_ID, variables.get("transactionId"));
                    exchange.setProperty(CHANNEL_REQUEST, variables.get("channelRequest"));

                    exchange.setProperty(BILLS_ACTION, "bills");

                    TransactionChannelRequestDTO channelRequest = objectMapper.readValue(exchange.getProperty(CHANNEL_REQUEST, String.class), TransactionChannelRequestDTO.class);
                    exchange.setProperty(BILL_REFERENCE, channelRequest.getPayee().getPartyIdInfo().getPartyIdentifier());
                    exchange.setProperty(IDENTIFIER_TYPE, channelRequest.getPayer().getPartyIdInfo().getPartyIdType());
                    exchange.setProperty(IDENTIFIER, channelRequest.getPayer().getPartyIdInfo().getPartyIdentifier());

                    producerTemplate.send("direct:bills-route-base", exchange);

                    if (!exchange.getProperty(BILLS_CALL_FAILED, Boolean.class)) {
                        Bill[] bills = objectMapper.readValue(exchange.getProperty(BILLS, String.class), Bill[].class);
                        boolean billPresent = false;

                        for (Bill bill: bills) {
                            if (bill.getBillReference().equals(exchange.getProperty(BILL_REFERENCE))) {
                                billPresent = true;
                                break;
                            }
                        }

                        if (billPresent)
                            variables.put(BILLS_STATUS_ERROR, false);
                        else {
                            variables.put(BILLS_STATUS_ERROR, true);
                            variables.put(ERROR_INFORMATION, "Bill Reference not present in account.");
                        }
                    } else {
                        variables.put(BILLS_STATUS_ERROR, true);
                        variables.put(ERROR_INFORMATION, exchange.getProperty(ERROR_INFORMATION, String.class));
                    }

                    client.newCompleteCommand(job.getKey())
                            .variables(variables)
                            .send()
                            .join();
                })
                .name("validateBill")
                .maxJobsActive(workerMaxJobs)
                .open();
    }

}
