-- Adds WINDOWS_QUEUE as a third printer connection type (PRINT-07 manual verification,
-- 2026-08-26): some generic ESC/POS thermal printers (e.g. this session's DP80UL-06) enumerate
-- on Windows as a native USB printer-class device with its own print queue, not as a serial/COM
-- port (jSerialComm's USB connection type) and not as a network device. windows_queue_name holds
-- the Windows print queue name (e.g. "Epson ESC/P 9pin V4 Class Driver") the agent submits raw
-- ESC/POS bytes to via the Windows spooler's RAW datatype, bypassing the queue's own driver
-- rendering.

ALTER TABLE printer_configs
    DROP CONSTRAINT printer_configs_connection_type_check;

ALTER TABLE printer_configs
    ADD CONSTRAINT printer_configs_connection_type_check
    CHECK (connection_type IN ('NETWORK', 'USB', 'WINDOWS_QUEUE'));

ALTER TABLE printer_configs
    ADD COLUMN windows_queue_name varchar(255);
