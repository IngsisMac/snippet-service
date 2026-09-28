# snippet-service

Microservicio principal del TP2 (**Snippet Playground**) para la gestión de metadata de snippets, estados de conformidad y casos de prueba.

## Responsabilidades

- CRUD de metadata de snippets (`id`, `name`, `owner_id`, `language`, `version`).
- Estado de conformidad (`PENDING`, `COMPLIANT`, `NOT_COMPLIANT`, `FAILED`) y conteo de hallazgos.
- Gestión de casos de prueba (`TestCase`: inputs, expected outputs, env).
- Consumo del stream de resultados de Redis para actualización de estado (`ResultConsumer`).

## Tecnologías

- **Kotlin 2.0** + **Java 21**.
- **Spring Boot 3.3** (Web, Data JPA, Security OAuth2 Resource Server).
- **PostgreSQL** + **Flyway** (migraciones de schema).
- **SpringDoc OpenAPI 3** (Swagger UI).
- **Testcontainers** (PostgreSQL) para tests de integración.

## Ejecución Local

```bash
./gradlew bootRun
```

Para correr las pruebas unitarias y de integración:

```bash
./gradlew check
```
