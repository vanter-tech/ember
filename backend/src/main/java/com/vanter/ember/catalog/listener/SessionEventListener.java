package com.vanter.ember.catalog.listener;

import com.vanter.ember.catalog.service.RestaurantTableService;
import com.vanter.ember.session.event.SessionOpened;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionEventListener {

    private final RestaurantTableService tableService;

    @EventListener
    public void onSessionOpened(SessionOpened event) {
        tableService.setOccupied(event.tableId());
    }
}
