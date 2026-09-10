package com.vanter.ember.printing.dto;

import com.vanter.ember.printing.model.DiscoveredPrinter;
import java.util.List;

public record ReportDiscoveredPrintersRequest(List<DiscoveredPrinter> printers) {}
