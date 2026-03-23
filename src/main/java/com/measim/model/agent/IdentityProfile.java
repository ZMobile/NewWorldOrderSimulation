package com.measim.model.agent;

import java.util.Random;
import java.util.Set;

public record IdentityProfile(
        String id, String name, Archetype archetype,
        double riskTolerance, double ambition, double altruism,
        double creativity, double complianceDisposition,
        Set<String> skillDomains
) {
    public static IdentityProfile fromArchetype(String id, String name, Archetype archetype,
                                                 Set<String> skills, Random rng) {
        return new IdentityProfile(id, name, archetype,
                perturb(archetype.riskTolerance(), rng), perturb(archetype.ambition(), rng),
                perturb(archetype.altruism(), rng), perturb(archetype.creativity(), rng),
                perturb(archetype.complianceDisposition(), rng), skills);
    }

    private static double perturb(double base, Random rng) {
        return Math.max(0.0, Math.min(1.0, base + rng.nextGaussian() * 0.05));
    }
}
