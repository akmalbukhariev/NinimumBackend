package com.ninimum.api.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.io.FileInputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Firebase Cloud Messaging (HTTP v1). The service account key is a file on the server (FIREBASE_SERVICE_ACCOUNT);
 * without it push is off. Sends in the background; a phone Firebase no longer knows is forgotten.
 */
@Slf4j
@Service
public class PushSender implements Push {
    private final JdbcTemplate jdbc;
    private final GoogleCredentials credentials;
    private final String project;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> { var t = new Thread(r, "push-sender"); t.setDaemon(true); return t; });
    private final ObjectMapper json = new ObjectMapper();

    public PushSender(JdbcTemplate jdbc, @Value("${FIREBASE_SERVICE_ACCOUNT:}") String keyFile) {
        this.jdbc = jdbc;
        GoogleCredentials c = null; String p = null;
        if (!keyFile.isBlank()) try (var in = new FileInputStream(keyFile)) {
            var account = ServiceAccountCredentials.fromStream(in);
            p = account.getProjectId();
            c = account.createScoped(List.of("https://www.googleapis.com/auth/firebase.messaging"));
        } catch (Exception ex) { log.warn("Push off: the Firebase key could not be read ({})", ex.getClass().getSimpleName()); }
        credentials = c; project = p;
        if (enabled()) log.info("Push on for Firebase project {}", project);
    }

    @Override public boolean enabled() { return credentials != null && project != null; }

    @Override
    public void send(String app, String owner, String title, String body, Map<String, String> data) {
        if (!enabled()) return;
        List<String> tokens = owner == null
                ? jdbc.queryForList("SELECT token FROM push_tokens WHERE app=?", String.class, app)
                : jdbc.queryForList("SELECT token FROM push_tokens WHERE app=? AND owner=?", String.class, app, owner);
        for (String token : tokens) worker.submit(() -> post(token, title, body, data));
    }

    private void post(String token, String title, String body, Map<String, String> data) {
        try {
            synchronized (credentials) { credentials.refreshIfExpired(); }
            var message = Map.of("message", Map.of("token", token,
                    "notification", Map.of("title", title, "body", body),
                    "data", data,
                    "android", Map.of("priority", "HIGH", "notification", Map.of("channel_id", "ninimum_orders", "sound", "default"))));
            var request = HttpRequest.newBuilder(URI.create("https://fcm.googleapis.com/v1/projects/" + project + "/messages:send"))
                    .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + credentials.getAccessToken().getTokenValue())
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(message))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 || response.body().contains("UNREGISTERED")) jdbc.update("DELETE FROM push_tokens WHERE token=?", token);
            else if (response.statusCode() >= 300) log.warn("Push failed: HTTP {}", response.statusCode());
        } catch (Exception ex) { log.warn("Push failed: {}", ex.getClass().getSimpleName()); }
    }
}
