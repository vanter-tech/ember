package com.vanter.ember.session.dto;

import jakarta.validation.constraints.Size;

/** Add a name-only Hub seat. A blank/absent name is auto-assigned the next free "Asiento N". */
public record AddSeatRequest(@Size(max = 50) String name) {
}
