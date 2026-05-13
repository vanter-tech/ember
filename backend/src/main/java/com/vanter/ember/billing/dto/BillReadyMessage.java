package com.vanter.ember.billing.dto;

import com.vanter.ember.billing.model.BillSplit;
import java.math.BigDecimal;
import java.util.List;

public record BillReadyMessage(String type, Long billId, BigDecimal total, List<BillSplit> splits) {

    public static BillReadyMessage of(Long billId, BigDecimal total, List<BillSplit> splits) {
        return new BillReadyMessage("BILL_READY", billId, total, splits);
    }
}
