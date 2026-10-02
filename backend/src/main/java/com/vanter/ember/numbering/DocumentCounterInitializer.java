package com.vanter.ember.numbering;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a series' counter row on first use, in its own transaction: two first-ever issuers racing
 * on the insert would otherwise poison the caller's transaction with a unique violation.
 */
@Component
@RequiredArgsConstructor
class DocumentCounterInitializer {

    private final DocumentCounterRepository counters;
    private final RestaurantRepository restaurants;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureExists(UUID tenantId, DocumentSeries series) {
        if (counters.existsById(new DocumentCounter.Key(tenantId, series))) {
            return;
        }
        Restaurant restaurant = restaurants.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + tenantId));
        // A racing first issuer makes this insert fail and roll back THIS transaction only; the caller
        // ({@link DocumentNumberService}) swallows that and uses the winner's row.
        counters.saveAndFlush(DocumentCounter.builder()
                .tenantId(tenantId)
                .series(series)
                .prefix(DocumentCodes.prefixFromSlug(restaurant.getSlug()))
                .lastNumber(0)
                .build());
    }
}
