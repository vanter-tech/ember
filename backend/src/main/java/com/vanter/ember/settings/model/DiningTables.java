package com.vanter.ember.settings.model;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "dining_table",
        indexes = @Index(name = "idx_dining_table_tenant", columnList = "restaurant_id"))
public class DiningTables {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Column(name = "table_number", nullable = false)
    private Integer tableNumber;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

}
