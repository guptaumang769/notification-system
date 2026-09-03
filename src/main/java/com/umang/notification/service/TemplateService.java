package com.umang.notification.service;

import com.umang.notification.exception.TemplateNotFoundException;
import com.umang.notification.model.entity.Template;
import com.umang.notification.model.enums.Channel;
import com.umang.notification.repository.TemplateRepository;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Loads templates and renders them by substituting {@code {{placeholder}}} tokens with
 * request params. A tiny regex renderer keeps this dependency-free; a production system
 * might swap in Handlebars/Freemarker for loops and conditionals.
 */
@Service
@RequiredArgsConstructor
public class TemplateService {

    /** Matches {{ token }} allowing surrounding whitespace. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*}}");

    private final TemplateRepository templateRepository;

    /** Rendered subject + body pair. */
    public record Rendered(String subject, String body) {
    }

    /**
     * Render the template for {@code (templateKey, channel)} against {@code params}.
     * Missing params are left as the literal placeholder so a gap is visible rather than
     * silently blanked.
     *
     * @throws TemplateNotFoundException if no template exists for the key+channel
     */
    public Rendered render(String templateKey, Channel channel, Map<String, String> params) {
        Template template = templateRepository
                .findByTemplateKeyAndChannel(templateKey, channel)
                .orElseThrow(() -> new TemplateNotFoundException(templateKey, channel));

        String subject = substitute(template.getSubject(), params);
        String body = substitute(template.getBody(), params);
        return new Rendered(subject, body);
    }

    /** Substitute every {@code {{key}}} in {@code text} with {@code params.get(key)}. */
    public String substitute(String text, Map<String, String> params) {
        if (text == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = params.getOrDefault(key, matcher.group(0));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
