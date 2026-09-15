package uk.co.rocketpub.staffportal.security;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RequestOriginValidator {
    private final String allowedOrigin;

    public RequestOriginValidator(
            @Value("${rocket.email.frontend-url:https://rocketpubserver.co.uk/staff}")
            String frontendUrl) {
        URI frontendUri = URI.create(frontendUrl);
        this.allowedOrigin = frontendUri.getScheme() + "://" + frontendUri.getRawAuthority();
    }

    public void requireAllowed(String origin) {
        if (origin == null || !allowedOrigin.equalsIgnoreCase(origin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Request origin is not allowed");
        }
    }
}
