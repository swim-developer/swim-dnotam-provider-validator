# swim-dnotam-provider-validator — Knowledge Base


## What This Is

**Mock ANSP (conformance test client) for validating the DNOTAM Provider.** Simulates an external ANSP subscriber, tests the provider against the EUR SWIM Registry service definition (117 conformance scenarios).

This is what tests `swim-digital-notam-provider`. It creates subscriptions, receives events, and validates provider behavior.

## What It Does

| Component | Purpose |
|-----------|---------|
| **Subscription UI** | Creates/manages subscriptions against the provider REST API |
| **AMQP Consumer** | Connects to the provider's Artemis broker, receives DNOTAM events |
| **Conformance Validator** | 117 test scenarios covering EUR SWIM Registry compliance |
| **Event Visualization** | Displays received events for manual inspection |

## Provider Connection Config

```yaml
# Points to swim-digital-notam-provider (this is the ONLY case where a validator points to the actual service)
swimServiceBaseURL: "https://dnotam-provider-<namespace>.apps.<cluster>"
amqpBrokerHost: "provider-artemis-<namespace>.apps.<cluster>"
```

## Build & Run

```bash
./mvnw clean package -DskipTests
quarkus dev
```
