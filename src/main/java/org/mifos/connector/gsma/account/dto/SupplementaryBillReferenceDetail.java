package org.mifos.connector.gsma.account.dto;

public class SupplementaryBillReferenceDetail {

    private String paymentReferenceType;
    private String paymentReferenceValue;

    public String getPaymentReferenceType() {
        return paymentReferenceType;
    }

    public void setPaymentReferenceType(String paymentReferenceType) {
        this.paymentReferenceType = paymentReferenceType;
    }

    public String getPaymentReferenceValue() {
        return paymentReferenceValue;
    }

    public void setPaymentReferenceValue(String paymentReferenceValue) {
        this.paymentReferenceValue = paymentReferenceValue;
    }
}
