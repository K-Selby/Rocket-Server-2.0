package uk.co.rocketpub.staffportal.email;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpSession;

@Service
public class RocketEmailService {
    private static final String AUTHORITY = "https://login.microsoftonline.com/consumers/oauth2/v2.0";
    private static final String SCOPE = "offline_access Mail.Send";
    private static final String FLOW = "rocketMicrosoftEmailFlow";

    private final JsonMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final SecureRandom random = new SecureRandom();

    private String accessToken;
    private Instant accessExpiresAt = Instant.EPOCH;
    private long connectionVersion;

    @Value("${rocket.email.client-id:}") private String clientId;
    @Value("${rocket.email.client-secret:}") private String clientSecret;
    @Value("${rocket.email.redirect-uri:http://localhost:8080/api/email/microsoft/callback}") private String redirectUri;
    @Value("${rocket.email.frontend-url:http://localhost:3000}") private String frontendUrl;
    @Value("${rocket.email.sender:rocketpubserver@outlook.com}") private String sender;
    @Value("${rocket.email.token-path:../../data/microsoft_email_token.json}") private String tokenPath;

    private record Flow(String state, String verifier, Instant expiresAt, long version) {}

    public RocketEmailService(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public synchronized RocketEmailStatus getStatus() {
        return new RocketEmailStatus(configured(), configured() && getAccessToken() != null, sender);
    }

    public synchronized String startMicrosoftConnection(HttpSession session) {
        if (!configured()) throw new IllegalStateException("Microsoft Client ID and Client Secret are not configured.");

        String state = randomValue();
        String verifier = randomValue();
        session.setAttribute(FLOW, new Flow(state, verifier, Instant.now().plusSeconds(600), connectionVersion));

        try {
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));

            return AUTHORITY + "/authorize?" + form(Map.of(
                    "client_id", clientId,
                    "response_type", "code",
                    "redirect_uri", redirectUri,
                    "response_mode", "query",
                    "scope", SCOPE,
                    "state", state,
                    "code_challenge", challenge,
                    "code_challenge_method", "S256",
                    "login_hint", sender));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not start Microsoft connection.");
        }
    }

    public synchronized String completeMicrosoftConnection(String code, String state, HttpSession session) {
        Object stored = session.getAttribute(FLOW);
        session.removeAttribute(FLOW);

        String failure = frontendUrl + "/manager?email=connection-failed";

        if (!(stored instanceof Flow flow)
                || !flow.state().equals(state)
                || flow.expiresAt().isBefore(Instant.now())
                || flow.version() != connectionVersion
                || code == null
                || code.isBlank()) return failure;

        try {
            Map<String, Object> result = requestToken(Map.of(
                    "grant_type", "authorization_code",
                    "code", code,
                    "code_verifier", flow.verifier()));

            String refresh = value(result, "refresh_token");
            if (refresh == null || value(result, "access_token") == null) return failure;

            saveRefreshToken(refresh);
            cacheAccessToken(result);
            connectionVersion++;

            return frontendUrl + "/manager?email=connected";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure;
        } catch (IOException | RuntimeException exception) {
            return failure;
        }
    }

    public synchronized void disconnect() {
        try {
            Files.deleteIfExists(path());
            accessToken = null;
            accessExpiresAt = Instant.EPOCH;
            connectionVersion++;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not disconnect Rocket Email.");
        }
    }

    public void sendVerificationEmail(String recipient, String code) {
        sendEmail(
                recipient,
                "Rocket Staff Portal email verification",
                "Your Rocket Staff Portal verification code is:\n\n" + code
                        + "\n\nThis code expires in 15 minutes.\n\n"
                        + "If you did not request this code, you can ignore this email.");
    }

    public void sendPasswordResetEmail(String recipient, String code) {
        sendEmail(
                recipient,
                "Rocket Staff Portal password reset",
                "A password reset was requested for your Rocket Staff Portal account.\n\n"
                        + "Your reset code is:\n\n" + code
                        + "\n\nThis code expires in 15 minutes.\n\n"
                        + "If you did not request a password reset, you can ignore this email.");
    }

    public synchronized void sendEmail(String recipient, String subject, String body) {
        String token = getAccessToken();
        if (token == null) throw new IllegalStateException("Rocket Email is not connected.");

        Map<String, Object> payload = Map.of(
                "message", Map.of(
                        "subject", subject,
                        "body", Map.of("contentType", "Text", "content", body),
                        "toRecipients", List.of(Map.of("emailAddress", Map.of("address", recipient)))),
                "saveToSentItems", true);

        HttpRequest request = HttpRequest.newBuilder(URI.create("https://graph.microsoft.com/v1.0/me/sendMail"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 202) throw new IllegalStateException("Microsoft could not send the email.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The email request was interrupted.");
        } catch (IOException exception) {
            throw new IllegalStateException("The email could not be sent.");
        }
    }

    private String getAccessToken() {
        if (!configured()) return null;
        if (accessToken != null && Instant.now().isBefore(accessExpiresAt)) return accessToken;

        try {
            if (!Files.exists(path())) return null;

            Map<String, Object> saved = mapper.readValue(Files.readString(path()), new TypeReference<>() {});
            String refresh = value(saved, "refreshToken");
            if (refresh == null) return null;

            Map<String, Object> result = requestToken(Map.of(
                    "grant_type", "refresh_token",
                    "refresh_token", refresh));

            if (value(result, "access_token") == null) return null;

            String rotated = value(result, "refresh_token");
            if (rotated != null) saveRefreshToken(rotated);

            cacheAccessToken(result);
            return accessToken;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private Map<String, Object> requestToken(Map<String, String> grant) throws IOException, InterruptedException {
        Map<String, String> values = new LinkedHashMap<>(grant);
        values.putAll(Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "redirect_uri", redirectUri,
                "scope", SCOPE));

        HttpRequest request = HttpRequest.newBuilder(URI.create(AUTHORITY + "/token"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(values)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("Microsoft authentication failed.");

        return mapper.readValue(response.body(), new TypeReference<>() {});
    }

    private void cacheAccessToken(Map<String, Object> result) {
        Object expires = result.get("expires_in");
        long seconds = expires instanceof Number number ? number.longValue() : 0;

        accessToken = value(result, "access_token");
        accessExpiresAt = Instant.now().plusSeconds(Math.max(0, seconds - 60));
    }

    private void saveRefreshToken(String refresh) throws IOException {
        Path destination = path();
        Files.createDirectories(destination.getParent());

        Path temporary = Files.createTempFile(destination.getParent(), "microsoft_email_token-", ".tmp");

        try {
            if (Files.getFileStore(temporary).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
            }

            Files.writeString(temporary, mapper.writeValueAsString(Map.of(
                    "refreshToken", refresh,
                    "updatedAt", Instant.now().toString())));

            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path path() {
        return Path.of(tokenPath).toAbsolutePath().normalize();
    }

    private boolean configured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    private String randomValue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String value(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private String form(Map<String, String> values) {
        return values.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue())).collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
