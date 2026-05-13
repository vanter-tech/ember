package com.vanter.ember.billing.repository;

import com.vanter.ember.billing.model.BillSplit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillSplitRepository extends JpaRepository<BillSplit, Long> {

    List<BillSplit> findByBillId(Long billId);

    Optional<BillSplit> findByBillIdAndParticipantName(Long billId, String participantName);
}
