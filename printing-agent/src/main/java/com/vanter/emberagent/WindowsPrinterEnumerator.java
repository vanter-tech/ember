package com.vanter.emberagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Enumerates the local Windows print queues (spec §2.3) via {@code Get-Printer}, reported wholesale
 * to the backend on every connect/refetch. Built as a standalone class so the future Hub-local
 * printer-detection plan can reuse it in-process. Never throws — {@code []} off Windows or on any
 * failure.
 */
public class WindowsPrinterEnumerator {

    private static final Logger log = System.getLogger(WindowsPrinterEnumerator.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // EcoTank / inkjet / photo lines that have no ESC/POS and only print through their driver.
    private static final Pattern INKJET = Pattern.compile(
            "(?i)(inkjet|ecotank|deskjet|officejet|pixma|stylus|expression|workforce|\\bL\\d{3,4}\\b|\\bET-\\d{3,4}\\b)");

    public List<DiscoveredPrinter> enumerate() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return List.of();
        }
        try {
            Process p = new ProcessBuilder(
                    "powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "Get-Printer | Select-Object Name,DriverName,PortName | ConvertTo-Json -Compress")
                    .redirectErrorStream(true)
                    .start();
            String out = readAll(p.getInputStream());
            p.waitFor();
            return parse(out);
        } catch (Exception e) {
            log.log(Level.WARNING, "No se pudo enumerar impresoras de Windows: " + e.getMessage());
            return List.of();
        }
    }

    static List<DiscoveredPrinter> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            List<DiscoveredPrinter> result = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(n -> result.add(toPrinter(n)));
            } else if (root.isObject()) {
                result.add(toPrinter(root)); // ConvertTo-Json emits a bare object for a single printer
            }
            return result;
        } catch (Exception e) {
            log.log(Level.WARNING, "Salida de Get-Printer no parseable: " + e.getMessage());
            return List.of();
        }
    }

    private static DiscoveredPrinter toPrinter(JsonNode n) {
        String name = n.path("Name").asText("");
        String driver = n.path("DriverName").asText("");
        String port = n.path("PortName").asText("");
        return new DiscoveredPrinter(name, driver, port, looksLikeInkjet(driver));
    }

    static boolean looksLikeInkjet(String driverName) {
        return driverName != null && INKJET.matcher(driverName).find();
    }

    private static String readAll(InputStream in) throws Exception {
        try (in; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            in.transferTo(bos);
            return bos.toString(StandardCharsets.UTF_8);
        }
    }
}
