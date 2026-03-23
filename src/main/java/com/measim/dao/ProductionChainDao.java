package com.measim.dao;

import com.measim.model.economy.ProductionChain;

import java.util.List;
import java.util.Optional;

public interface ProductionChainDao {
    void register(ProductionChain chain);
    Optional<ProductionChain> getById(String id);
    List<ProductionChain> getAllDiscovered();
    List<ProductionChain> getAll();
}
