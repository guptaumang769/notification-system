-- V2: seed a few templates and users/preferences so the demo works out of the box.

-- Templates: one business key ("welcome", "order-shipped") per channel, with
-- {{placeholder}} tokens the renderer substitutes at delivery time.
INSERT INTO templates (template_key, channel, subject, body, created_at, updated_at) VALUES
    ('welcome', 'EMAIL', 'Welcome, {{name}}!',
     'Hi {{name}}, thanks for joining {{product}}. Your account is ready.', now(), now()),
    ('welcome', 'SMS', NULL,
     'Welcome to {{product}}, {{name}}! Reply STOP to opt out.', now(), now()),
    ('welcome', 'PUSH', 'Welcome!',
     '{{name}}, tap to finish setting up {{product}}.', now(), now()),
    ('order-shipped', 'EMAIL', 'Your order {{orderId}} has shipped',
     'Hi {{name}}, order {{orderId}} is on its way. Track it at {{trackingUrl}}.', now(), now()),
    ('order-shipped', 'SMS', NULL,
     'Order {{orderId}} shipped! Track: {{trackingUrl}}', now(), now()),
    ('order-shipped', 'PUSH', 'Order shipped',
     'Order {{orderId}} is on the way.', now(), now());

-- User preferences: user-1 takes everything; user-2 has opted out of SMS and set a
-- quiet-hours window (22:00–07:00 UTC) that a production system would honour.
INSERT INTO user_preferences
    (user_id, email_enabled, sms_enabled, push_enabled, quiet_hours_start, quiet_hours_end, created_at, updated_at)
VALUES
    ('user-1', TRUE,  TRUE,  TRUE,  NULL, NULL, now(), now()),
    ('user-2', TRUE,  FALSE, TRUE,  22,   7,    now(), now());
