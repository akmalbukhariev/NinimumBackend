package com.ninimum.api.payment.refund;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Read-only Subscribe API client; never invokes receipts.cancel or receipts.pay. */
@Component
public class PaymeReceiptClient {
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final boolean enabled;
    private final String auth;
    private final String url;
    public PaymeReceiptClient(ObjectMapper json,
            @Value("${payme.refund-monitor.enabled:false}") boolean enabled,
            @Value("${payme.refund-monitor.x-auth:}") String auth,
            @Value("${payme.refund-monitor.url:https://checkout.paycom.uz/api}") String url) {
        this.json=json; this.enabled=enabled; this.auth=auth; this.url=url;
    }
    public boolean available() { return enabled && !auth.isBlank(); }
    public JsonNode receipt(String id) throws Exception {
        if (!available()) throw new IllegalStateException("NOT_CONFIGURED");
        URI uri=URI.create(url);
        if (!"https".equals(uri.getScheme()) || !("checkout.paycom.uz".equals(uri.getHost())
                || "checkout.test.paycom.uz".equals(uri.getHost()))) throw new IllegalStateException("INVALID_ENDPOINT");
        String body=json.writeValueAsString(Map.of("id",1,"method","receipts.get","params",Map.of("id",id)));
        HttpRequest request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("X-Auth",auth).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200) throw new IllegalStateException("PROVIDER_UNAVAILABLE");
        JsonNode root=json.readTree(response.body());
        if(root.hasNonNull("error")) throw new IllegalStateException("PROVIDER_ERROR");
        JsonNode receipt=root.path("result").path("receipt");
        if(!receipt.isObject() || !receipt.path("state").isIntegralNumber()) throw new IllegalStateException("INVALID_RESPONSE");
        return receipt;
    }
}
