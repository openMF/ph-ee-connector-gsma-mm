package org.mifos.connector.gsma.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.mifos.connector.common.channel.dto.TransactionChannelRequestDTO;
import org.mifos.connector.common.mojaloop.type.IdentifierType;
import org.mifos.connector.gsma.transfer.dto.GSMATransaction;
import org.mifos.connector.gsma.transfer.dto.Party;
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

        Party msisdnCreditParty = new Party();
        msisdnCreditParty.setKey(channelRequest.getPayee().getPartyIdInfo().getPartyIdType().toString().toLowerCase());
        msisdnCreditParty.setValue(channelRequest.getPayee().getPartyIdInfo().getPartyIdentifier());

        Party[] creditParty = new Party[]{ msisdnCreditParty };

        Party msisdnDebitParty = new Party();
        msisdnDebitParty.setKey(channelRequest.getPayer().getPartyIdInfo().getPartyIdType().toString().toLowerCase());
        msisdnDebitParty.setValue(channelRequest.getPayer().getPartyIdInfo().getPartyIdentifier());

        Party[] debitParty = new Party[]{ msisdnDebitParty };

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
