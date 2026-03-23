package com.measim.model.gamemaster;

import java.util.Set;

public record TechNode(
        String id,
        String name,
        Set<String> prerequisites,
        int depth,
        boolean discovered
) {}
