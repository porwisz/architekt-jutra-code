package pl.devstyle.aj.footprint.audit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
class FootprintAuditListener {

    private final FootprintAuditRepository repository;
    private final FootprintAuditMapper mapper;
    private final Counter failedCounter;
    private final Timer persistTimer;

    FootprintAuditListener(
            FootprintAuditRepository repository,
            FootprintAuditMapper mapper,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.mapper = mapper;
        this.failedCounter = meterRegistry.counter("footprint.audit.failed");
        this.persistTimer = meterRegistry.timer("footprint.audit.persist");
    }

    /**
     * Audit persistence runs asynchronously after the publisher's transaction commits.
     * <ul>
     *   <li>{@code @Async("footprintAuditExecutor")} keeps the HTTP response thread free
     *       even if the audit insert is slow.</li>
     *   <li>{@code REQUIRES_NEW} gives the listener its own transaction (the publisher tx
     *       has already committed by the time AFTER_COMMIT fires).</li>
     *   <li>{@code @Retryable} retries transient DB failures with configurable backoff.</li>
     *   <li>Idempotency: a duplicate {@code correlation_id} (unique constraint) is treated
     *       as a successful audit — the row already exists, so we short-circuit instead of
     *       deterministically exhausting retries on the same conflict.</li>
     * </ul>
     */
    @Async("footprintAuditExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Retryable(
            maxAttemptsExpression = "${app.footprint.audit.retry.max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${app.footprint.audit.retry.delay-ms:200}",
                    multiplierExpression = "${app.footprint.audit.retry.multiplier:2.0}"))
    public void onFootprintCalculated(FootprintCalculatedEvent event) {
        if (event.dryRun()) {
            return;
        }
        persistTimer.record(() -> {
            try {
                repository.save(mapper.toEntity(event));
            } catch (DataIntegrityViolationException duplicate) {
                // Audit row for this correlationId already exists (e.g. retried after commit).
                // Treat as success and stop retrying.
                log.debug("Audit row already exists for correlationId={} — treating as idempotent success",
                        event.correlationId());
            }
        });
    }

    @Recover
    public void recoverFromExhaustedRetries(Exception ex, FootprintCalculatedEvent event) {
        log.error("Footprint audit persistence exhausted retries for correlationId={} (counter footprint.audit.failed incremented)",
                event.correlationId(), ex);
        failedCounter.increment();
    }
}
