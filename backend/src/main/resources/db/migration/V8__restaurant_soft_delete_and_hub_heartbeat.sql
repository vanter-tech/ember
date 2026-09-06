-- Piece B: soft-delete a suspended tenant (reversible). DELETED joins the
-- RestaurantStatus enum as another "not ACTIVE" value; deleted_at/deleted_by are audit metadata.
ALTER TABLE restaurants
    ADD COLUMN deleted_at timestamp(6) with time zone,
    ADD COLUMN deleted_by uuid;

-- Piece C: cloud records each verified Hub heartbeat so the console can show liveness.
ALTER TABLE hub_activations
    ADD COLUMN last_heartbeat_at timestamp(6) with time zone,
    ADD COLUMN last_heartbeat_ip varchar(45);
