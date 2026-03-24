package com.measim.dao;

import com.measim.model.service.AgentServiceType;
import com.measim.model.service.ServiceInstance;
import com.measim.model.world.HexCoord;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class ServiceDaoImpl implements ServiceDao {

    private final Map<String, AgentServiceType> types = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, ServiceInstance> instances = new java.util.concurrent.ConcurrentHashMap<>();

    @Override public void registerType(AgentServiceType type) { types.put(type.id(), type); }
    @Override public Optional<AgentServiceType> getType(String id) { return Optional.ofNullable(types.get(id)); }
    @Override public List<AgentServiceType> getAllTypes() { return List.copyOf(types.values()); }
    @Override public List<AgentServiceType> getTypesByCategory(AgentServiceType.ServiceCategory cat) {
        return types.values().stream().filter(t -> t.category() == cat).toList();
    }

    @Override public void addInstance(ServiceInstance instance) { instances.put(instance.id(), instance); }
    @Override public Optional<ServiceInstance> getInstance(String id) { return Optional.ofNullable(instances.get(id)); }
    @Override public List<ServiceInstance> getActiveInstances() {
        return instances.values().stream().filter(ServiceInstance::isActive).toList();
    }
    @Override public List<ServiceInstance> getInstancesByOwner(String agentId) {
        return instances.values().stream().filter(i -> i.ownerId().equals(agentId) && i.isActive()).toList();
    }
    @Override public List<ServiceInstance> getInstancesByCategory(AgentServiceType.ServiceCategory cat) {
        return instances.values().stream().filter(i -> i.isActive() && i.type().category() == cat).toList();
    }
    @Override public List<ServiceInstance> getInstancesNear(HexCoord location, int radius) {
        return instances.values().stream()
                .filter(i -> i.isActive() && i.location() != null && i.location().distanceTo(location) <= radius).toList();
    }
}
