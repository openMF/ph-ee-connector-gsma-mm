package org.mifos.connector.gsma.camel.config;

/**
 * Central Definition of all the Camel Exchange Properties
 */
public class CamelProperties {

    private CamelProperties() {}

    public static final String SOME_PROPERTY = "someProperty";
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
    public static final String TRANSACTION_LINK = "transactionLink";
    public static final String TRANSACTION_OBJECT = "transactionObject";
    public static final String TRANSACTION_OBJECT_AVAILABLE = "transactionObjectAvailable";
    public static final String IS_API_CALL = "isAPICall";
    public static final String GSMA_AUTHORIZATION_CODE = "gsmaAuthorizationCode";
}
