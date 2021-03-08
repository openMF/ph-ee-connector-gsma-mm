package org.mifos.connector.gsma.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.mifos.connector.common.channel.dto.TransactionChannelRequestDTO;
import org.mifos.connector.common.gsma.dto.GSMATransaction;
import org.mifos.connector.common.gsma.dto.GsmaParty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.mifos.connector.gsma.camel.config.CamelProperties.*;

@Component
public class TransformRequestData implements Processor {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void process(Exchange exchange) throws Exception {

        TransactionChannelRequestDTO channelRequest = objectMapper.readValue(exchange.getProperty(CHANNEL_REQUEST, String.class), TransactionChannelRequestDTO.class);
        GSMATransaction gsmaTransaction = new GSMATransaction();

        GsmaParty msisdnCreditParty = new GsmaParty();
        msisdnCreditParty.setKey(channelRequest.getPayee().getPartyIdInfo().getPartyIdType().toString().toLowerCase());
        msisdnCreditParty.setValue(channelRequest.getPayee().getPartyIdInfo().getPartyIdentifier());

        GsmaParty[] creditParty = new GsmaParty[]{ msisdnCreditParty };

        GsmaParty msisdnDebitParty = new GsmaParty();
        msisdnDebitParty.setKey(channelRequest.getPayer().getPartyIdInfo().getPartyIdType().toString().toLowerCase());
        msisdnDebitParty.setValue(channelRequest.getPayer().getPartyIdInfo().getPartyIdentifier());

        GsmaParty[] debitParty = new GsmaParty[]{ msisdnDebitParty };

        String amount = channelRequest.getAmount().getAmount();
        String currency = channelRequest.getAmount().getCurrency();

        if (exchange.getProperty(GSMA_AUTHORIZATION_CODE, String.class) != null)
            gsmaTransaction.setOneTimeCode(exchange.getProperty(GSMA_AUTHORIZATION_CODE, String.class));

        gsmaTransaction.setAmount(amount);
        gsmaTransaction.setCurrency(currency);
        gsmaTransaction.setCreditParty(creditParty);
        gsmaTransaction.setDebitParty(debitParty);

        exchange.setProperty(TRANSACTION_BODY, gsmaTransaction);
    }
}
