# Notification System — Diagrams

Mermaid diagrams (render natively on GitHub). Generated from the actual entities under
`src/main/java/com/umang/notification/model/entity/` and the service/consumer layer.

- [1. High-Level Design (HLD)](#1-high-level-design-hld)
- [2. Delivery Sequence](#2-delivery-sequence)
- [3. Notification State Machine](#3-notification-state-machine)
- [4. Domain Class Diagram](#4-domain-class-diagram)

---

## 1. High-Level Design (HLD)

Event-driven pub/sub: producers publish and return; the consumer renders, checks
preferences + rate limit, sends via the channel Strategy, and records status. Idempotency
and rate limiting live in Redis; the durable store is Postgres; poison records go to the DLT.

```mermaid
flowchart TB
    subgraph Producers
      Client[REST client / upstream service]
    end

    Client -->|POST /api/v1/notifications| API[NotificationController<br/>+ IngestionService]
    API -->|SETNX dedupe at edge| Redis[(Redis<br/>idempotency + rate limit)]
    API -->|publish 1 event per channel| Kafka{{Kafka<br/>notification-requests}}

    Sched[ScheduledNotificationPoller<br/>@Scheduled] -->|promote due SCHEDULED| Kafka
    API -->|future sendAt ⇒ SCHEDULED row| PG[(PostgreSQL<br/>Flyway-managed)]

    Kafka --> Consumer[NotificationConsumer]

    subgraph Delivery[NotificationDeliveryService pipeline]
      Consumer --> Idem[1. Idempotency SETNX]
      Idem --> Pref[2. Preference opt-out]
      Pref --> Rate[3. Rate limit]
      Rate --> Render[4. TemplateService render]
      Render --> Factory[5. ChannelFactory Strategy]
    end

    Idem -. duplicate .-> Drop[drop]
    Rate -. over cap .-> PG
    Pref -. opted out .-> PG

    Factory --> Email[EmailSender → SES]
    Factory --> Sms[SmsSender → SNS]
    Factory --> Push[PushSender → FCM]

    Email --> PG
    Sms --> PG
    Push --> PG

    Rate -->|counter/TTL| Redis
    Idem -->|SETNX| Redis

    Kafka -. retries exhausted .-> DLT{{notification-requests.DLT}}

    Delivery -.metrics.-> Prom[(Prometheus)]
```

---

## 2. Delivery Sequence

The asynchronous path of one notification, from publish to a durable `SENT` row — including
the retry → DLT branch on transient channel failure.

```mermaid
sequenceDiagram
    participant P as Producer
    participant API as IngestionService
    participant K as Kafka
    participant C as Consumer / DeliveryService
    participant R as Redis
    participant CH as Channel (SES/SNS/FCM)
    participant DB as PostgreSQL

    P->>API: POST /notifications {channels, templateKey, idempotencyKey}
    API->>K: publish NotificationRequestedEvent (per channel)
    API-->>P: 202 Accepted

    K->>C: deliver(event)
    C->>R: SETNX idempotencyKey
    alt duplicate (key already set)
        C-->>K: return (drop, no send)
    else first delivery
        C->>DB: preference opt-in? rate cap ok?
        C->>C: render template with params
        C->>CH: send(recipient, subject, body)
        alt success
            CH-->>C: ok
            C->>DB: upsert status = SENT
        else transient failure
            CH-->>C: TransientChannelException
            C->>DB: upsert status = FAILED (attempt++)
            C-->>K: rethrow ⇒ retry w/ backoff
            Note over K,C: retries exhausted → DeadLetterPublishingRecoverer
            K->>K: publish to notification-requests.DLT
        end
    end
```

---

## 3. Notification State Machine

The `status` column on `notifications`. `QUEUED` and `SCHEDULED` are the two entry states;
everything else is terminal for that attempt.

```mermaid
stateDiagram-v2
    [*] --> SCHEDULED : sendAt in future
    [*] --> QUEUED : send now (published to Kafka)
    SCHEDULED --> QUEUED : poller promotes due row
    QUEUED --> SENT : channel accepted
    QUEUED --> SKIPPED : user opted out of channel
    QUEUED --> RATE_LIMITED : per-user cap exceeded
    QUEUED --> FAILED : permanent error, or transient retries exhausted → DLT
    SENT --> [*]
    SKIPPED --> [*]
    RATE_LIMITED --> [*]
    FAILED --> [*]
```

---

## 4. Domain Class Diagram

```mermaid
classDiagram
    class NotificationRequest {
      +String userId
      +String eventKey
      +List~Channel~ channels
      +String templateKey
      +Map params
      +String idempotencyKey
      +Instant sendAt
    }
    class NotificationRequestedEvent {
      +String idempotencyKey
      +String userId
      +String eventKey
      +Channel channel
      +String templateKey
      +Map params
      +Instant sendAt
      +Instant timestamp
    }
    class Notification {
      +Long id
      +String userId
      +String eventKey
      +Channel channel
      +String templateKey
      +NotificationStatus status
      +String failureReason
      +int attempts
      +String idempotencyKey
      +Instant sendAt
      +Instant sentAt
    }
    class Template {
      +Long id
      +String templateKey
      +Channel channel
      +String subject
      +String body
    }
    class UserPreference {
      +Long id
      +String userId
      +boolean emailEnabled
      +boolean smsEnabled
      +boolean pushEnabled
      +Integer quietHoursStart
      +Integer quietHoursEnd
      +isChannelEnabled(Channel) boolean
    }
    class NotificationChannel {
      <<interface>>
      +channel() Channel
      +send(recipient, subject, body)
    }
    class EmailSender
    class SmsSender
    class PushSender
    class ChannelFactory {
      +forChannel(Channel) NotificationChannel
    }
    class Channel {
      <<enumeration>>
      EMAIL
      SMS
      PUSH
    }
    class NotificationStatus {
      <<enumeration>>
      SCHEDULED
      QUEUED
      SENT
      FAILED
      RATE_LIMITED
      SKIPPED
    }

    NotificationRequest --> NotificationRequestedEvent : fans out per channel
    NotificationRequestedEvent --> Notification : persisted as
    Notification --> Channel
    Notification --> NotificationStatus
    Template --> Channel
    UserPreference --> Channel
    NotificationChannel <|.. EmailSender
    NotificationChannel <|.. SmsSender
    NotificationChannel <|.. PushSender
    ChannelFactory --> NotificationChannel : selects by Channel
```
