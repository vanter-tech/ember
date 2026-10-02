package com.vanter.ember.numbering;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues the next gap-free number of a tenant's series. The counter row is locked and bumped inside
 * the caller's transaction, so a rolled-back document gives its number back and two concurrent
 * issuers can never get the same one.
 */
@Service
@RequiredArgsConstructor
public class DocumentNumberService {

    private final DocumentCounterRepository counters;
    private final DocumentCounterInitializer initializer;

    @Transactional(propagation = Propagation.REQUIRED)
    public IssuedNumber next(UUID tenantId, DocumentSeries series) {
        try {
            initializer.ensureExists(tenantId, series);
        } catch (DataIntegrityViolationException alreadyCreatedByARacingIssuer) {
            // The other issuer's row is the one that counts; the lock below serializes us behind it.
        }
        DocumentCounter counter = counters.findForUpdate(tenantId, series)
                .orElseThrow(() -> new IllegalStateException("Counter missing after init: " + tenantId + "/" + series));
        int number = counter.getLastNumber() + 1;
        counter.setLastNumber(number);
        counters.save(counter);
        return new IssuedNumber(number, series.format(counter.getPrefix(), number));
    }
}
