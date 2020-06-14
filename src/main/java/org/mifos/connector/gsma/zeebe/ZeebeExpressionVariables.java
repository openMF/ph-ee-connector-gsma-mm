package org.mifos.connector.gsma.zeebe;

public class ZeebeExpressionVariables {

    public static final String SAMPLE_EXPRESSION_SUCCESS = "sampleExpressionSuccess";
    public static final String PARTY_LOOKUP_FAILED = "partyLookupFailed";
    public static final String PAYEE_LOOKUP_RETRY_COUNT = "payeeAccountStatusRetry";
    public static final String TRANSFER_RETRY_COUNT = "paymentTransferRetry";
    public static final String TRANSACTION_FAILED = "transactionFailed";
    public static final String TRANSFER_STATE = "transferState";

    private ZeebeExpressionVariables() {
    }

}