package com.rideshare.user;

import com.rideshare.common.exception.ErrorCode;
import com.rideshare.common.exception.InvalidRequestException;
import com.rideshare.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Only addresses from the configured institute domain(s) may register.
 * Subdomains are accepted too (e.g. @cse.iitbbs.ac.in for iitbbs.ac.in).
 */
@Component
public class EmailDomainValidator {

    private final List<String> allowedDomains;

    public EmailDomainValidator(AppProperties properties) {
        this.allowedDomains = properties.auth().allowedEmailDomains().stream()
                .map(domain -> domain.trim().toLowerCase(Locale.ROOT))
                .filter(domain -> !domain.isEmpty())
                .toList();
    }

    public boolean isAllowed(String email) {
        if (email == null) {
            return false;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        int at = normalized.lastIndexOf('@');
        if (at <= 0 || at == normalized.length() - 1) {
            return false;
        }
        String domain = normalized.substring(at + 1);
        return allowedDomains.stream()
                .anyMatch(allowed -> domain.equals(allowed) || domain.endsWith("." + allowed));
    }

    public void requireAllowed(String email) {
        if (!isAllowed(email)) {
            throw new InvalidRequestException(ErrorCode.INVALID_EMAIL_DOMAIN,
                    "Please register with your institute email (" + String.join(", ", allowedDomains) + ")");
        }
    }

    public List<String> allowedDomains() {
        return allowedDomains;
    }
}
