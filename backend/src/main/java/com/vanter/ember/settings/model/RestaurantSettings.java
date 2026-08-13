package com.vanter.ember.settings.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;
import java.util.UUID;

@Data
@Entity
@Table(name = "restaurant_settings")

public class RestaurantSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "restaurant_id", unique = true, nullable = false, updatable = false)
    private UUID restaurantId;

    // No columnDefinition: the hardcoded "jsonb" made the table uncreatable outside PostgreSQL.
    // SqlTypes.JSON already resolves to jsonb on PostgreSQL and to the dialect's own JSON type
    // elsewhere, so DDL and binding stay in agreement on both prod and the H2 test schema.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private SettingsPayload payload;
}
