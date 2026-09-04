package com.umang.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.umang.notification.exception.TemplateNotFoundException;
import com.umang.notification.model.entity.Template;
import com.umang.notification.model.enums.Channel;
import com.umang.notification.repository.TemplateRepository;
import com.umang.notification.service.TemplateService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Placeholder substitution is the core of the template service. */
@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock
    private TemplateRepository templateRepository;

    @InjectMocks
    private TemplateService templateService;

    @Test
    void substitutesPlaceholdersInSubjectAndBody() {
        Template template = Template.builder()
                .templateKey("welcome")
                .channel(Channel.EMAIL)
                .subject("Welcome, {{name}}!")
                .body("Hi {{name}}, thanks for joining {{product}}.")
                .build();
        when(templateRepository.findByTemplateKeyAndChannel("welcome", Channel.EMAIL))
                .thenReturn(Optional.of(template));

        TemplateService.Rendered rendered = templateService.render(
                "welcome", Channel.EMAIL, Map.of("name", "Umang", "product", "Notify"));

        assertThat(rendered.subject()).isEqualTo("Welcome, Umang!");
        assertThat(rendered.body()).isEqualTo("Hi Umang, thanks for joining Notify.");
    }

    @Test
    void leavesUnknownPlaceholdersLiteralAndHandlesWhitespace() {
        // Whitespace inside braces is tolerated; a missing param stays as the literal token.
        String out = templateService.substitute(
                "Hi {{ name }}, code {{missing}}", Map.of("name", "Umang"));
        assertThat(out).isEqualTo("Hi Umang, code {{missing}}");
    }

    @Test
    void throwsWhenTemplateMissing() {
        when(templateRepository.findByTemplateKeyAndChannel("nope", Channel.SMS))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> templateService.render("nope", Channel.SMS, Map.of()))
                .isInstanceOf(TemplateNotFoundException.class);
    }
}
