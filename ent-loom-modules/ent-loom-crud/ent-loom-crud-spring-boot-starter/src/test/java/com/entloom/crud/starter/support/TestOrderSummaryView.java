package com.entloom.crud.starter.support;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TestOrderSummaryView {
    private Long id;
    private String orderNo;
    private BigDecimal payAmount;
    private Boolean paid;
}
