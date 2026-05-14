package com.ninimum.api.constants;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum UserOrCompanyStatus {
    ACTIVE("ACTIVE"),
    INACTIVE("INACTIVE"),
    BANNED("BANNED");

    private final String value;

    UserOrCompanyStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static UserOrCompanyStatus fromValue(String value) {
        for (UserOrCompanyStatus type : UserOrCompanyStatus.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown UserOrCompanyStatus value: " + value);
    }
}
