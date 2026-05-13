package com.vanter.ember.kitchen.controller;

import com.vanter.ember.kitchen.dto.KitchenDisplayEntry;
import com.vanter.ember.kitchen.dto.UpdateItemStatusRequest;
import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.kitchen.service.KitchenService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kitchen")
@RequiredArgsConstructor
public class KitchenController {

    private final KitchenService kitchenService;

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('KITCHEN', 'ADMIN')")
    public List<KitchenOrder> getAllOrders() {
        return kitchenService.findAll();
    }

    @GetMapping("/display")
    @PreAuthorize("hasAnyRole('KITCHEN', 'ADMIN')")
    public List<KitchenDisplayEntry> getDisplay() {
        return kitchenService.findDisplay();
    }

    @GetMapping("/orders/{sessionId}")
    @PreAuthorize("hasAnyRole('KITCHEN', 'ADMIN')")
    public KitchenOrder getOrderBySessionId(@PathVariable String sessionId) {
        return kitchenService.findBySessionId(sessionId);
    }

    @PatchMapping("/orders/{orderId}/items/{itemId}/status")
    @PreAuthorize("hasRole('KITCHEN')")
    public KitchenOrder updateItemStatus(@PathVariable String orderId,
                                         @PathVariable String itemId,
                                         @Valid @RequestBody UpdateItemStatusRequest request) {
        return kitchenService.updateItemStatus(orderId, itemId, request.status());
    }
}
