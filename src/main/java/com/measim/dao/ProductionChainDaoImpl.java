package com.measim.dao;

import com.measim.model.economy.ProductionChain;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class ProductionChainDaoImpl implements ProductionChainDao {

    private final List<ProductionChain> chains = new ArrayList<>();
    private final Map<String, ProductionChain> index = new HashMap<>();

    @Override
    public void register(ProductionChain chain) {
        chains.add(chain);
        index.put(chain.id(), chain);
    }

    @Override
    public Optional<ProductionChain> getById(String id) { return Optional.ofNullable(index.get(id)); }

    @Override
    public List<ProductionChain> getAllDiscovered() {
        return chains.stream().filter(ProductionChain::isDiscovered).toList();
    }

    @Override
    public List<ProductionChain> getAll() { return Collections.unmodifiableList(chains); }
}
