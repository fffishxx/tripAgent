package com.tripagent.taxi.constant;

public final class TaxiProvider {

    public static final String MOCK = "MOCK";
    public static final String DIDI_MCP = "DIDI_MCP";

    private TaxiProvider() {
    }

    public static boolean isValid(String provider) {
        return MOCK.equals(provider) || DIDI_MCP.equals(provider);
    }
}
