-- WINDOWS_QUEUE's RAW-ESC/POS approach (V3) only works against a real ESC/POS-capable printer.
-- The EPSON L3210 Series (inkjet, no ESC/POS support) used for PRINT-07's ongoing verification
-- needs the queue's own Windows driver to rasterize plain text instead — render_mode lets the
-- agent pick per printer. Only meaningful for WINDOWS_QUEUE; NETWORK/USB configs stay RAW and
-- the agent ignores the column for them.

ALTER TABLE printer_configs
    ADD COLUMN render_mode varchar(20) NOT NULL DEFAULT 'RAW';

ALTER TABLE printer_configs
    ADD CONSTRAINT printer_configs_render_mode_check
    CHECK (render_mode IN ('RAW', 'DRIVER'));
