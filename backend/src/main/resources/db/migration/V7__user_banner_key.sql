-- Customer home banner preset. Stores the BannerKey enum name (EMBER, SUNSET, ...);
-- null means the client falls back to the default preset. Only CUSTOMER rows use it today.
ALTER TABLE users ADD COLUMN banner_key VARCHAR(20);
