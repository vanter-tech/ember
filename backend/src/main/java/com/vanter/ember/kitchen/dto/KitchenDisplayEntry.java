package com.vanter.ember.kitchen.dto;

import com.vanter.ember.kitchen.model.KitchenOrder;
import java.util.List;

public record KitchenDisplayEntry(int tableNumber, List<KitchenOrder> orders) {}
