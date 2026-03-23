package com.measim.service.scoring;

import com.measim.dao.AuditDaoImpl;
import com.measim.model.scoring.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScoringServiceTest {

    private final ScoringServiceImpl service = new ScoringServiceImpl(new AuditDaoImpl());
    private final SectorBaseline baseline = new SectorBaseline();

    @Test void efModifier_cleanProducer() { assertEquals(1.10, service.computeEfModifier(0.3)); }
    @Test void efModifier_dirtyProducer() { assertEquals(0.60, service.computeEfModifier(2.5)); }
    @Test void efModifier_atBaseline() { assertEquals(1.00, service.computeEfModifier(0.9)); }
    @Test void ccModifier_highContributor() { assertEquals(1.08, service.computeCcModifier(0.95)); }
    @Test void ccModifier_lowContributor() { assertEquals(0.97, service.computeCcModifier(0.1)); }
    @Test void ldDiversion_atSectorNorm() { assertEquals(0.0, service.computeLdDiversion(1.0)); }

    @Test void ldDiversion_highlyAutomated() {
        assertEquals(0.05, service.computeLdDiversion(3.0), 0.001);
    }

    @Test void ldDiversion_cappedAt25Percent() {
        assertEquals(0.25, service.computeLdDiversion(100.0), 0.001);
    }

    @Test void rcModifier_belowThreshold() { assertEquals(1.00, service.computeRcModifier(5.0)); }

    @Test void rcModifier_moderateWealth() {
        assertEquals(0.96, service.computeRcModifier(50.0), 0.001);
    }

    @Test void rcModifier_extremeWealth() {
        assertEquals(0.30, service.computeRcModifier(5000.0), 0.001);
    }

    @Test void combinedModifiers_neutralAgent() {
        ScoreVector score = new ScoreVector();
        score.setEnvironmentalFootprint(1.0);
        score.setCommonsContribution(0.5);
        score.setLaborDisplacement(1.0);
        score.setResourceConcentration(1.0);
        ModifierSet mods = service.computeModifiers(score, baseline);
        assertEquals(1.0, mods.combinedMultiplier(), 0.001);
    }
}
