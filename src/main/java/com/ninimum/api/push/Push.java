package com.ninimum.api.push;

import java.util.Map;

/** Sends a notification to every phone of one app, or of one account in it (owner null = everyone in that app). */
public interface Push {
    String CUSTOMER = "CUSTOMER", WAREHOUSE = "WAREHOUSE", COURIER = "COURIER";
    boolean enabled();
    void send(String app, String owner, String title, String body, Map<String, String> data);
}
