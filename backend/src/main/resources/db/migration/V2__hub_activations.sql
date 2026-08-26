CREATE TABLE hub_activations (
    id uuid NOT NULL,
    restaurant_id uuid NOT NULL,
    hardware_fingerprint varchar(255) NOT NULL,
    activated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_hub_activations PRIMARY KEY (id),
    CONSTRAINT uk_hub_activations_restaurant_id UNIQUE (restaurant_id)
);
