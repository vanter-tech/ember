package com.vanter.ember.session.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Serialises "who gets this table" decisions. The row lock on the target table is taken at the start
 * of a short programmatic transaction, before the occupancy check, so two waiters racing for the
 * same free table (a seat vs a link, or two links onto different sessions) cannot both pass the
 * check. Programmatic rather than {@code @Transactional} so the caller publishes its events after
 * the commit, never inside the lock.
 */
@Component
public class TableLock {

    private final DiningTableRepository diningTableRepository;
    private final TransactionTemplate transactionTemplate;

    public TableLock(DiningTableRepository diningTableRepository, PlatformTransactionManager transactionManager) {
        this.diningTableRepository = diningTableRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public <T> T withTableLock(UUID tableId, Function<DiningTables, T> action) {
        return transactionTemplate.execute(status -> {
            DiningTables table = diningTableRepository.findByIdForUpdate(tableId)
                    .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + tableId));
            return action.apply(table);
        });
    }
}
