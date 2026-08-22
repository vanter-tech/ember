package com.vanter.ember.printing.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.printing.dto.CreatePrinterConfigRequest;
import com.vanter.ember.printing.dto.PrinterConfigResponse;
import com.vanter.ember.printing.dto.UpdatePrinterConfigRequest;
import com.vanter.ember.printing.model.ConnectionType;
import com.vanter.ember.printing.model.PrintAgent;
import com.vanter.ember.printing.model.PrinterConfig;
import com.vanter.ember.printing.model.PrinterRole;
import com.vanter.ember.printing.repository.PrintAgentRepository;
import com.vanter.ember.printing.repository.PrinterConfigRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PrinterConfigService {

    private final PrinterConfigRepository printerConfigRepository;
    private final PrintAgentRepository printAgentRepository;

    @Transactional
    public PrinterConfigResponse create(UUID tenantId, UUID agentId, CreatePrinterConfigRequest request) {
        PrintAgent agent = printAgentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Print agent not found: " + agentId));
        if (!agent.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Print agent not found: " + agentId);
        }

        PrinterConfig config = printerConfigRepository.save(PrinterConfig.builder()
                .id(UUID.randomUUID())
                .agentId(agentId)
                .role(PrinterRole.valueOf(request.role()))
                .connectionType(ConnectionType.valueOf(request.connectionType()))
                .host(request.host())
                .port(request.port())
                .comPort(request.comPort())
                .label(request.label())
                .active(true)
                .build());
        return toResponse(config);
    }

    @Transactional
    public PrinterConfigResponse update(UUID tenantId, UUID printerId, UpdatePrinterConfigRequest request) {
        PrinterConfig config = getOwned(tenantId, printerId);
        if (request.host() != null) config.setHost(request.host());
        if (request.port() != null) config.setPort(request.port());
        if (request.comPort() != null) config.setComPort(request.comPort());
        if (request.label() != null) config.setLabel(request.label());
        if (request.active() != null) config.setActive(request.active());
        return toResponse(printerConfigRepository.save(config));
    }

    public List<PrinterConfigResponse> listByAgent(UUID tenantId, UUID agentId) {
        return printerConfigRepository.findByAgentId(agentId).stream()
                .filter(c -> c.getTenantId().equals(tenantId))
                .map(this::toResponse)
                .toList();
    }

    private PrinterConfig getOwned(UUID tenantId, UUID printerId) {
        PrinterConfig config = printerConfigRepository.findById(printerId)
                .orElseThrow(() -> new ResourceNotFoundException("Printer not found: " + printerId));
        if (!config.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Printer not found: " + printerId);
        }
        return config;
    }

    private PrinterConfigResponse toResponse(PrinterConfig config) {
        return new PrinterConfigResponse(
                config.getId(), config.getAgentId(), config.getRole().name(),
                config.getConnectionType().name(), config.getHost(), config.getPort(),
                config.getComPort(), config.getLabel(), config.isActive());
    }
}
