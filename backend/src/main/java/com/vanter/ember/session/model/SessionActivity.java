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
        TABLE_TRANSFERRED,
        PARTICIPANT_LEFT,
        /** An ADMIN closed a stuck table; {@code participantName} = the admin's email, {@code note} = why. */
        CLOSED_BY_ADMIN,
        /** A free table was attached to the session; {@code note} = the table label, e.g. {@code M4}. */
        TABLE_LINKED,
        /** A linked table was detached; {@code note} = the table label. */
        TABLE_UNLINKED
    }

    private Type type;
    private String itemName;
    private String participantName;
    private LocalDateTime timestamp;
    /** Free-text detail (used by {@link Type#CLOSED_BY_ADMIN}); null for the older entry types. */
    private String note;
}
