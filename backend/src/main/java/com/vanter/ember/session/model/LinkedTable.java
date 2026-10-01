package com.vanter.ember.session.model;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A table physically joined to a session's primary table. {@code tableNumber} is denormalised so
 * tickets and reports never need a lookup and survive a table being renumbered or deactivated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkedTable {
    private UUID tableId;
    private int tableNumber;
    private LocalDateTime linkedAt;
}
