package com.umang.notification.repository;

import com.umang.notification.model.entity.Template;
import com.umang.notification.model.enums.Channel;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateRepository extends JpaRepository<Template, Long> {

    Optional<Template> findByTemplateKeyAndChannel(String templateKey, Channel channel);
}
