package org.mifos.connector.gsma.transfer;

import org.mifos.connector.common.channel.dto.TransactionChannelRequestDTO;
import org.mifos.connector.common.gsma.dto.QuotesDTO;
import org.springframework.stereotype.Component;

@Component
public class DataTransformer {

    public QuotesDTO getQuoteRequestBody(TransactionChannelRequestDTO channelRequest) {
        QuotesDTO quote = new QuotesDTO();



        return quote;
    }

}
