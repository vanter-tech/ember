package com.vanter.ember.session.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionActivity {

    public enum Type {
        ITEM_SENT,
        ITEM_DELETED,
        TABLE_TRANSFERRED
    }

    private Type type;
    private String itemName;
    private String participantName;
    private LocalDateTime timestamp;
}
