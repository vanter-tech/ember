package com.vanter.ember.printing.service;

import com.vanter.ember.printing.model.PrinterConfig;
import com.vanter.ember.printing.model.PrinterRole;
import com.vanter.ember.printing.repository.PrinterConfigRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * On an on-premise Hub every caja PC runs its own print agent next to its own printer, and the
 * browser that asks for a receipt reaches the Hub from that same PC. So a receipt is routed to
 * the agent connected from the requester's address, instead of to every active printer of the
 * role. Off by default: the cloud sits behind proxies where a source address means nothing, and
 * a restaurant with a single printer per role needs no routing.
 *
 * <p>Routing only happens when it is unambiguous — exactly one connected agent at that address,
 * and that agent has an active printer for the role. Anything else (a phone or tablet with no
 * agent, no matching agent, two agents on one PC, not inside a web request) returns empty, and
 * the caller keeps the historical behavior of sending to all printers of the role.
 */
@Component
public class PrintTargetResolver {

    private final PrintAgentConnectionRegistry connectionRegistry;
    private final PrinterConfigRepository printerConfigRepository;
    private final boolean routeBySourceIp;

    public PrintTargetResolver(
            PrintAgentConnectionRegistry connectionRegistry,
            PrinterConfigRepository printerConfigRepository,
            @Value("${ember.printing.route-by-source-ip:false}") boolean routeBySourceIp) {
        this.connectionRegistry = connectionRegistry;
        this.printerConfigRepository = printerConfigRepository;
        this.routeBySourceIp = routeBySourceIp;
    }

    /** Resolves against the HTTP request bound to the current thread, if any. */
    public Optional<UUID> resolveForCurrentRequest(UUID tenantId, PrinterRole role) {
        if (!routeBySourceIp) {
            return Optional.empty();
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return Optional.empty();
        }
        HttpServletRequest request = servlet.getRequest();
        return resolve(tenantId, role, request.getRemoteAddr());
    }

    Optional<UUID> resolve(UUID tenantId, PrinterRole role, String requesterAddress) {
        List<UUID> agents = connectionRegistry.connectedAgentsAt(SourceIps.normalize(requesterAddress));
        if (agents.size() != 1) {
            return Optional.empty();
        }
        UUID agentId = agents.get(0);
        List<PrinterConfig> printers = printerConfigRepository.findByTenantIdAndRoleAndActiveTrue(tenantId, role);
        return printers.stream().anyMatch(p -> agentId.equals(p.getAgentId()))
                ? Optional.of(agentId)
                : Optional.empty();
    }
}
