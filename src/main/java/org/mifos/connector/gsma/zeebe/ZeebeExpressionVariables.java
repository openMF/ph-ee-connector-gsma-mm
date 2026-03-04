package org.mifos.connector.gsma.zeebe;

public final class ZeebeExpressionVariables {

    public static final String SAMPLE_EXPRESSION_SUCCESS = "sampleExpressionSuccess";
    public static final String PARTY_LOOKUP_FAILED = "partyLookupFailed";
    public static final String PAYEE_LOOKUP_RETRY_COUNT = "payeeAccountStatusRetry";
    public static final String TRANSFER_RETRY_COUNT = "paymentTransferRetry";
    public static final String TRANSACTION_FAILED = "transactionFailed";
    public static final String TRANSFER_STATE = "transferState";
    public static final String LINK_CREATE_FAILED = "linkCreationFailed";
    public static final String LINK_CREATION_COUNT = "linkCreationRetryCount";
    public static final String NUMBER_OF_PAYMENTS = "numberOfPayments";
    public static final String BILLS_STATUS_ERROR = "billStatusError";
    public static final String QUOTE_RETRY_COUNT = "quoteRetryCount";

    private ZeebeExpressionVariables() {}

}
