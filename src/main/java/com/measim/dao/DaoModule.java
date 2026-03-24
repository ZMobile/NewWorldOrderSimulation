package com.measim.dao;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class DaoModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(WorldDao.class).to(WorldDaoImpl.class).in(Singleton.class);
        bind(AgentDao.class).to(AgentDaoImpl.class).in(Singleton.class);
        bind(MarketDao.class).to(MarketDaoImpl.class).in(Singleton.class);
        bind(AuditDao.class).to(AuditDaoImpl.class).in(Singleton.class);
        bind(ProductionChainDao.class).to(ProductionChainDaoImpl.class).in(Singleton.class);
        bind(MetricsDao.class).to(MetricsDaoImpl.class).in(Singleton.class);
        bind(LlmDao.class).to(LlmDaoImpl.class).in(Singleton.class);
        bind(TechnologyRegistryDao.class).to(TechnologyRegistryDaoImpl.class).in(Singleton.class);
        bind(GovernmentDao.class).to(GovernmentDaoImpl.class).in(Singleton.class);
        bind(InfrastructureDao.class).to(InfrastructureDaoImpl.class).in(Singleton.class);
        bind(CommunicationDao.class).to(CommunicationDaoImpl.class).in(Singleton.class);
        bind(RiskDao.class).to(RiskDaoImpl.class).in(Singleton.class);
        bind(ExternalityDao.class).to(ExternalityDaoImpl.class).in(Singleton.class);
        bind(ServiceDao.class).to(ServiceDaoImpl.class).in(Singleton.class);
        bind(PropertyDao.class).to(PropertyDaoImpl.class).in(Singleton.class);
        bind(ContractDao.class).to(ContractDaoImpl.class).in(Singleton.class);
    }
}
