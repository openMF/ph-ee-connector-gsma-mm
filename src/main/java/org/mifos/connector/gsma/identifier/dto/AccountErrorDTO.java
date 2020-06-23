package org.mifos.connector.gsma.identifier.dto;

import java.util.List;

public class AccountErrorDTO {
    public String errorCategory;
    public String errorCode;
    public String errorDescription;
    public String errorDateTime;
    public List<ErrorParameter> errorParameters = null;

    public String getErrorCategory() {
        return errorCategory;
    }

    public void setErrorCategory(String errorCategory) {
        this.errorCategory = errorCategory;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    public void setErrorDescription(String errorDescription) {
        this.errorDescription = errorDescription;
    }

    public String getErrorDateTime() {
        return errorDateTime;
    }

    public void setErrorDateTime(String errorDateTime) {
        this.errorDateTime = errorDateTime;
    }

    public List<ErrorParameter> getErrorParameters() {
        return errorParameters;
    }

    public void setErrorParameters(List<ErrorParameter> errorParameters) {
        this.errorParameters = errorParameters;
    }

    @Override
    public String toString() {
        return "AccountStatusError{" +
                "errorCategory='" + errorCategory + '\'' +
                ", errorCode='" + errorCode + '\'' +
                ", errordescription='" + errorDescription + '\'' +
                ", errorDateTime='" + errorDateTime + '\'' +
                ", errorParameters=" + errorParameters +
                '}';
    }
}
