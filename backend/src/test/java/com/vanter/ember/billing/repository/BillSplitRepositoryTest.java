package com.vanter.ember.billing.repository;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillSplit;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.SplitMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class BillSplitRepositoryTest {

    @Autowired BillRepository billRepository;
    @Autowired BillSplitRepository billSplitRepository;

    private Bill savedBill() {
        return billRepository.save(Bill.builder()
                .sessionId("sess-1").total(new BigDecimal("40.00"))
                .splitMethod(SplitMethod.BY_CONSUMPTION).status(BillStatus.OPEN)
                .createdAt(LocalDateTime.now()).build());
    }

    @Test
    void save_persistsBillSplit() {
        Bill bill = savedBill();
        BillSplit split = BillSplit.builder()
                .bill(bill).participantName("Alice")
                .amount(new BigDecimal("25.00")).paid(false).build();

        BillSplit saved = billSplitRepository.save(split);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getBill().getId()).isEqualTo(bill.getId());
        assertThat(saved.getParticipantName()).isEqualTo("Alice");
        assertThat(saved.getAmount()).isEqualByComparingTo("25.00");
        assertThat(saved.isPaid()).isFalse();
    }

    @Test
    void findByBillId_returnsAllSplitsForBill() {
        Bill bill = savedBill();
        billSplitRepository.save(BillSplit.builder()
                .bill(bill).participantName("Alice").amount(new BigDecimal("25.00")).paid(false).build());
        billSplitRepository.save(BillSplit.builder()
                .bill(bill).participantName("Bob").amount(new BigDecimal("15.00")).paid(false).build());

        List<BillSplit> splits = billSplitRepository.findByBillId(bill.getId());

        assertThat(splits).hasSize(2);
    }

    @Test
    void findByBillIdAndParticipantName_returnsSplit() {
        Bill bill = savedBill();
        billSplitRepository.save(BillSplit.builder()
                .bill(bill).participantName("Alice").amount(new BigDecimal("25.00")).paid(false).build());

        Optional<BillSplit> found = billSplitRepository.findByBillIdAndParticipantName(bill.getId(), "Alice");

        assertThat(found).isPresent();
        assertThat(found.get().getParticipantName()).isEqualTo("Alice");
    }
}
