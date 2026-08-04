package com.docusense.backend.service;

import org.springframework.stereotype.Service;
import java.util.regex.Pattern;

@Service
public class PiiRedactorService {

    // Common PII Regex Patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+"
    );
    private static final Pattern SSN_PATTERN = Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b"
    );
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:\\d[ -]*?){13,16}\\b"
    );
    private static final Pattern IP_PATTERN = Pattern.compile(
            "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b"
    );

    public String redact(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String redacted = text;
        redacted = EMAIL_PATTERN.matcher(redacted).replaceAll("[REDACTED_EMAIL]");
        redacted = SSN_PATTERN.matcher(redacted).replaceAll("[REDACTED_SSN]");
        redacted = PHONE_PATTERN.matcher(redacted).replaceAll("[REDACTED_PHONE]");
        redacted = CREDIT_CARD_PATTERN.matcher(redacted).replaceAll("[REDACTED_CREDIT_CARD]");
        redacted = IP_PATTERN.matcher(redacted).replaceAll("[REDACTED_IP]");

        return redacted;
    }
}
