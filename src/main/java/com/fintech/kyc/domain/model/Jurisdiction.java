package com.fintech.kyc.domain.model;

public enum Jurisdiction {
    GB("United Kingdom - FCA Regulated"),
    EU("European Union - AMLD6 Regulated"),
    US("United States - FinCEN Regulated"),
    SG("Singapore - MAS Regulated"),
    AU("Australia - AUSTRAC Regulated"),
    GLOBAL("Global Default Corridor");

    private final String description;

    Jurisdiction(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
