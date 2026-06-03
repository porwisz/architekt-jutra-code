# Phase 2 Scope Clarifications

Date: 2026-05-20

## Important defaults — confirmed/overridden

| ID | Decision | Source |
|---|---|---|
| audit-permission-scope | **Reuse PERMISSION_READ** for CSV export endpoint | Default accepted |
| problem-type-uri-base | **Externalize via @Value config property** (`app.footprint.problem-base-uri`) | Default accepted |
| liquibase-format | **YAML** Liquibase changelog (matches existing 10 files) | Default accepted |
| csv-library-choice | **Apache Commons CSV** dependency | **Override** (default was hand-rolled) |
| spring-retry-dependency | **Add spring-retry + spring-aspects + @EnableRetry** | Default accepted |

## Implications

- `pom.xml` gets two additions: `org.springframework.retry:spring-retry`, `org.springframework:spring-aspects`, plus `org.apache.commons:commons-csv` for the export slice.
- New `@Configuration` enables `@EnableRetry` (likely in `FootprintModuleConfig`).
- New config key `app.footprint.problem-base-uri` documented in `application.properties` (default e.g. `/problems`).
- No new Permission enum value; CSV export authorized by `PERMISSION_READ`.
- Liquibase changelog `011-create-footprint-audit-log.yaml` in YAML.

## No new critical decisions; proceeding to Phase 5.
