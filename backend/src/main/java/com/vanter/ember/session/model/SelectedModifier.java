package com.vanter.ember.session.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectedModifier {

    private String groupName;
    private String optionName;
    private BigDecimal priceDelta;
}
