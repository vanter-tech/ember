package com.vanter.ember.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class TableLockTest {

    @Mock DiningTableRepository diningTableRepository;
    @Mock PlatformTransactionManager transactionManager;
    TableLock tableLock;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        tableLock = new TableLock(diningTableRepository, transactionManager);
    }

    @Test
    void withTableLock_passesTheLockedRowToTheActionInsideATransactionAndCommits() {
        UUID id = UUID.randomUUID();
        DiningTables table = DiningTables.builder().id(id).tableNumber(4).build();
        when(diningTableRepository.findByIdForUpdate(id)).thenReturn(Optional.of(table));

        Integer number = tableLock.withTableLock(id, DiningTables::getTableNumber);

        assertThat(number).isEqualTo(4);
        verify(transactionManager).commit(any());
    }

    @Test
    void withTableLock_unknownTableIsNotFound() {
        UUID id = UUID.randomUUID();
        when(diningTableRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tableLock.withTableLock(id, t -> "x"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(transactionManager).rollback(any());
    }

    @Test
    void withTableLock_aFailingActionRollsBackAndPropagates() {
        UUID id = UUID.randomUUID();
        when(diningTableRepository.findByIdForUpdate(id))
                .thenReturn(Optional.of(DiningTables.builder().id(id).tableNumber(4).build()));

        assertThatThrownBy(() -> tableLock.withTableLock(id, t -> {
            throw new IllegalStateException("occupied");
        })).isInstanceOf(IllegalStateException.class).hasMessage("occupied");
        verify(transactionManager).rollback(any());
    }
}
