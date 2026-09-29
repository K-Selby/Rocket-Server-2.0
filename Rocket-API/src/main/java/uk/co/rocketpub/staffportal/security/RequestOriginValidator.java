package uk.co.rocketpub.staffportal.security;

import java.net.URI;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RequestOriginValidator {
    private final Set<String> allowedOrigins;

    public RequestOriginValidator(
            @Value("${rocket.email.frontend-url:https://rocketpubserver.co.uk/staff}")
            String frontendUrl) {
        URI frontendUri = URI.create(frontendUrl);
        String configuredOrigin = frontendUri.getScheme() + "://" + frontendUri.getRawAuthority();
        this.allowedOrigins = Set.of(configuredOrigin, "http://localhost:8000");
    }

    public void requireAllowed(String origin) {
        if (origin == null || allowedOrigins.stream().noneMatch(value -> value.equalsIgnoreCase(origin))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Request origin is not allowed");
        }
    }
}
