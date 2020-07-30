package org.mifos.connector.gsma.account.dto;

import org.mifos.connector.gsma.transfer.dto.Party;

public class LinksDTO {

    private String linkReference;
    private Party[] sourceAccountIdentifiers;
    private String status;
    private String mode;

    public String getLinkReference() {
        return linkReference;
    }

    public void setLinkReference(String linkReference) {
        this.linkReference = linkReference;
    }

    public Party[] getSourceAccountIdentifiers() {
        return sourceAccountIdentifiers;
    }

    public void setSourceAccountIdentifiers(Party[] sourceAccountIdentifiers) {
        this.sourceAccountIdentifiers = sourceAccountIdentifiers;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

}
