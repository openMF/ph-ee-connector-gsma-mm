package org.mifos.connector.gsma.account.dto;

import org.mifos.connector.gsma.transfer.dto.Party;

import java.util.Arrays;

public class AuthorizationCodeDTO {

    private String authorisationCode;
    private String codeState;
    private String codeLifetime;
    private String requestDate;
    private String amount;
    private String currency;
    private String amountType;
    private String holdFundsIndicator;

    private RedemptionChannel[] redemptionChannels;
    private RedemptionTransactionType[] redemptionTransactionTypes;
    private Party[] redemptionAccountIdentifiers;
    private Party[] metadata;

    public String getAuthorisationCode() {
        return authorisationCode;
    }

    public void setAuthorisationCode(String authorisationCode) {
        this.authorisationCode = authorisationCode;
    }

    public String getCodeState() {
        return codeState;
    }

    public void setCodeState(String codeState) {
        this.codeState = codeState;
    }

    public String getCodeLifetime() {
        return codeLifetime;
    }

    public void setCodeLifetime(String codeLifetime) {
        this.codeLifetime = codeLifetime;
    }

    public String getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(String requestDate) {
        this.requestDate = requestDate;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getAmountType() {
        return amountType;
    }

    public void setAmountType(String amountType) {
        this.amountType = amountType;
    }

    public String getHoldFundsIndicator() {
        return holdFundsIndicator;
    }

    public void setHoldFundsIndicator(String holdFundsIndicator) {
        this.holdFundsIndicator = holdFundsIndicator;
    }

    public RedemptionChannel[] getRedemptionChannels() {
        return redemptionChannels;
    }

    public void setRedemptionChannels(RedemptionChannel[] redemptionChannels) {
        this.redemptionChannels = redemptionChannels;
    }

    public RedemptionTransactionType[] getRedemptionTransactionTypes() {
        return redemptionTransactionTypes;
    }

    public void setRedemptionTransactionTypes(RedemptionTransactionType[] redemptionTransactionTypes) {
        this.redemptionTransactionTypes = redemptionTransactionTypes;
    }

    public Party[] getRedemptionAccountIdentifiers() {
        return redemptionAccountIdentifiers;
    }

    public void setRedemptionAccountIdentifiers(Party[] redemptionAccountIdentifiers) {
        this.redemptionAccountIdentifiers = redemptionAccountIdentifiers;
    }

    public Party[] getMetadata() {
        return metadata;
    }

    public void setMetadata(Party[] metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return "AuthorizationCodeDTO{" +
                "authorisationCode='" + authorisationCode + '\'' +
                ", codeState='" + codeState + '\'' +
                ", codeLifetime='" + codeLifetime + '\'' +
                ", requestDate='" + requestDate + '\'' +
                ", amount='" + amount + '\'' +
                ", currency='" + currency + '\'' +
                ", amountType='" + amountType + '\'' +
                ", holdFundsIndicator='" + holdFundsIndicator + '\'' +
                ", redemptionChannels=" + Arrays.toString(redemptionChannels) +
                ", redemptionTransactionTypes=" + Arrays.toString(redemptionTransactionTypes) +
                ", redemptionAccountIdentifiers=" + Arrays.toString(redemptionAccountIdentifiers) +
                ", metadata=" + Arrays.toString(metadata) +
                '}';
    }
}
