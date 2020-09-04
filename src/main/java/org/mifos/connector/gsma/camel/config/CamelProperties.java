package org.mifos.connector.gsma.camel.config;

/**
 * Central Definition of all the Camel Exchange Properties
 */
public class CamelProperties {

    private CamelProperties() {}

    public static final String ORIGIN_DATE = "originDate";
    public static final String AUTH_HOST = "authUrl";
    public static final String CLIENT_KEY = "clientKey";
    public static final String CLIENT_SECRET = "clientSecret";
    public static final String ACCESS_TOKEN = "accessToken";
    public static final String IDENTIFIER_TYPE = "identifier_type";
    public static final String IDENTIFIER = "identifier";
    public static final String ACCOUNT_RESPONSE = "accountResponse";
    public static final String ACCOUNT_ACTION = "actionAction";
    public static final String CORRELATION_ID = "correlationId";
    public static final String TRANSACTION_BODY = "transactionBody";
    public static final String TRANSACTION_TYPE = "transactionType";
    public static final String ERROR_INFORMATION = "errorInformation";
    public static final String TRANSACTION_ID = "transactionId";
    public static final String CHANNEL_REQUEST = "channelRequest";
    public static final String IS_RTP_REQUEST = "isRtpRequest";
    public static final String SERVER_CORRELATION = "isRtpRequest";
    public static final String TRANSACTION_STATUS = "transactionStatus";
    public static final String STATUS_AVAILABLE = "statusAvailable";
    public static final String RESPONSE_OBJECT_LINK = "responseObjectLink";
    public static final String RESPONSE_OBJECT = "responseObject";
    public static final String RESPONSE_OBJECT_AVAILABLE = "responseObjectAvailable";
    public static final String TRANSACTION_OBJECT_AVAILABLE = "transactionObjectAvailable";
    public static final String TRANSACTION_OBJECT = "transactionObject";
    public static final String IS_API_CALL = "isAPICall";
    public static final String GSMA_AUTHORIZATION_CODE = "gsmaAuthorizationCode";
    public static final String LINKS_REQUEST_BODY = "linksRequestBody";
    public static final String LINK_REFERENCE = "linkReference";
    public static final String RESPONSE_OBJECT_TYPE = "responseObjectType";
    public static final String TRANSACTION_CORRELATION_ID = "transactionCorrelationId";
    public static final String TRANSACTION_REFERENCE = "transactionReference";
    public static final String DEBIT_MANDATE_BODY = "debitMandateBody";
    public static final String MANDATE_REFERENCE = "mandateReference";
    public static final String MANDATE_CREATE_FAILED = "mandateCreateFailed";
    public static final String BILLS_ACTION = "billsAction";
    public static final String BILLS_CALL_FAILED = "billsCallFailed";
    public static final String BILL_COMPANIES = "billCompanies";
    public static final String BILLS = "bills";
    public static final String BILLS_REQUEST_BODY = "billsRequestBody";
    public static final String BILL_REFERENCE = "billReference";
    public static final String QUOTE_ID = "quoteId";
    public static final String QUOTE_REFERENCE = "quoteReference";
    public static final String GSMA_QUOTE_FAILED = "gsmaQuoteFailed";
    public static final String QUOTE_RESPONSE = "quoteResponse";
    public static final String QUOTE_REQUEST_BODY = "quoteRequestBody";
    public static final String GSMA_CHANNEL_REQUEST = "gsmaChannelRequest";
    public static final String RECEIVING_TENANT = "receivingTenant";

}
