# Research Brief: Reviews Plugin for Microkernel Platform

## Research Question

How should a new "reviews" plugin be designed and implemented for the existing microkernel-based Spring Boot platform?

## Research Type

**Mixed** — combines technical investigation (existing codebase/architecture) with requirements exploration (reviews domain) and literature research (best practices for plugin-based review systems).

## Scope

### Included
- Existing plugin architecture and patterns in the codebase (warehouse plugin, box-size plugin)
- Current plugin loading / registration mechanism
- Spring Boot integration points for plugins (JPA, REST, Liquibase)
- Reviews domain model: what constitutes a review, who reviews what, rating model
- Database migration strategy for new plugin table(s)
- Inter-plugin communication if reviews references other domain entities

### Excluded
- External third-party review platforms (Trustpilot, Google Reviews, etc.)
- Non-plugin / monolithic approaches
- Frontend implementation details (out of scope for initial research)

### Constraints
- Java 25, Spring Boot 4.0.5 (WebMVC, JPA, jOOQ, Liquibase)
- PostgreSQL database
- Must follow existing microkernel plugin pattern
- Must adhere to project standards in `.maister/docs/standards/`

## Success Criteria

1. Clear understanding of how existing plugins are structured and registered
2. Identified domain model for the reviews plugin (entities, relationships, enumerations)
3. Documented API design (REST endpoints) following project API standards
4. Database migration plan for reviews tables
5. Identification of any cross-plugin dependencies or shared abstractions needed
6. Actionable recommendations ready to feed into a development workflow

## Research Date

2026-04-07
