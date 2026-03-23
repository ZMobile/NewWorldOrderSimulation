package com.measim.dao;

import com.measim.model.service.AgentServiceType;
import com.measim.model.service.ServiceInstance;
import com.measim.model.world.HexCoord;

import java.util.List;
import java.util.Optional;

public interface ServiceDao {
    // Service types (GM-created blueprints)
    void registerType(AgentServiceType type);
    Optional<AgentServiceType> getType(String typeId);
    List<AgentServiceType> getAllTypes();
    List<AgentServiceType> getTypesByCategory(AgentServiceType.ServiceCategory category);

    // Service instances (running businesses)
    void addInstance(ServiceInstance instance);
    Optional<ServiceInstance> getInstance(String id);
    List<ServiceInstance> getActiveInstances();
    List<ServiceInstance> getInstancesByOwner(String agentId);
    List<ServiceInstance> getInstancesByCategory(AgentServiceType.ServiceCategory category);
    List<ServiceInstance> getInstancesNear(HexCoord location, int radius);
}
