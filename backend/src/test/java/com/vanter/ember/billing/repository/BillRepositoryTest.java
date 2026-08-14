package com.vanter.ember.billing.repository;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.SplitMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.vanter.ember.config.TenantIdentifierResolver;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TenantIdentifierResolver.class)
class BillRepositoryTest {

    @Autowired BillRepository billRepository;

    private Bill sampleBill() {
        return Bill.builder()
                .sessionId("sess-1")
                .total(new BigDecimal("45.00"))
                .splitMethod(SplitMethod.BY_CONSUMPTION)
                .status(BillStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void save_persistsBill() {
        Bill saved = billRepository.save(sampleBill());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSessionId()).isEqualTo("sess-1");
        assertThat(saved.getTotal()).isEqualByComparingTo("45.00");
        assertThat(saved.getSplitMethod()).isEqualTo(SplitMethod.BY_CONSUMPTION);
        assertThat(saved.getStatus()).isEqualTo(BillStatus.OPEN);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findBySessionId_returnsBill() {
        billRepository.save(sampleBill());

        Optional<Bill> found = billRepository.findBySessionId("sess-1");

        assertThat(found).isPresent();
        assertThat(found.get().getSessionId()).isEqualTo("sess-1");
    }

    @Test
    void findByStatus_returnsMatchingBills() {
        billRepository.save(sampleBill());
        billRepository.save(Bill.builder()
                .sessionId("sess-2").total(new BigDecimal("30.00"))
                .splitMethod(SplitMethod.EQUAL_PARTS).status(BillStatus.PAID)
                .createdAt(LocalDateTime.now()).build());

        List<Bill> openBills = billRepository.findByStatus(BillStatus.OPEN);

        assertThat(openBills).hasSize(1);
        assertThat(openBills.get(0).getSessionId()).isEqualTo("sess-1");
    }
}
