package com.measim.service.agentservice;

import com.measim.model.service.AgentServiceType;
import com.measim.model.service.ServiceInstance;
import com.measim.model.service.ServiceProposal;

import java.util.List;
import java.util.Optional;

/**
 * Manages agent-created services. Agent proposes → GM evaluates → service created.
 * Handles service operation, consumption, revenue, and lifecycle.
 */
public interface AgentServiceManager {

    /**
     * Agent proposes a new service. GM evaluates feasibility and sets properties.
     * Returns the created instance if approved.
     */
    Optional<ServiceInstance> proposeService(ServiceProposal proposal, int currentTick);

    /**
     * Agent consumes a service. Pays the price, receives the effects.
     * Returns true if successfully consumed.
     */
    boolean consumeService(String consumerId, String serviceInstanceId, int currentTick);

    /**
     * Process all active services for this tick: operating costs, capacity reset, reputation.
     */
    void tickServices(int currentTick);

    /**
     * Find available services of a category near a location.
     */
    List<ServiceInstance> findServices(AgentServiceType.ServiceCategory category,
                                        com.measim.model.world.HexCoord nearLocation, int radius);

    /**
     * Shut down a service (owner decides or forced by insolvency).
     */
    void shutdownService(String serviceId, int currentTick);
}
