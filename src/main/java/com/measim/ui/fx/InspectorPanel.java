package com.measim.ui.fx;

import com.measim.model.agent.Agent;
import com.measim.model.world.Tile;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

public class InspectorPanel extends VBox {

    private final Label titleLabel = new Label("Inspector");
    private final TextArea detailsArea = new TextArea();

    public InspectorPanel() {
        setSpacing(5);
        setPrefWidth(300);
        titleLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");
        detailsArea.setEditable(false);
        detailsArea.setWrapText(true);
        detailsArea.setPrefRowCount(30);
        getChildren().addAll(titleLabel, detailsArea);
    }

    public void showTile(Tile tile) {
        if (tile == null) {
            detailsArea.setText("No tile selected.");
            return;
        }
        titleLabel.setText("Tile (" + tile.coord().q() + ", " + tile.coord().r() + ")");
        StringBuilder sb = new StringBuilder();
        sb.append("Terrain: ").append(tile.terrain()).append("\n");
        sb.append("Settlement zone: ").append(tile.isSettlementZone()).append("\n");
        sb.append("Government: ").append(tile.governmentId() != null ? tile.governmentId() : "none").append("\n\n");

        var env = tile.environment();
        sb.append("=== Environment ===\n");
        sb.append(String.format("Soil: %.2f | Air: %.2f | Water: %.2f | Bio: %.2f%n",
                env.soilQuality(), env.airQuality(), env.waterQuality(), env.biodiversity()));
        sb.append(String.format("Average health: %.2f%s%n", env.averageHealth(),
                env.isCrisis() ? " [CRISIS]" : ""));

        sb.append("\n=== Resources ===\n");
        for (var res : tile.resources()) {
            sb.append(String.format("  %s: %.1f / %.1f%s%n", res.type(),
                    res.abundance(), res.maxAbundance(),
                    res.isDepleted() ? " [DEPLETED]" : ""));
        }

        sb.append("\nStructures: ").append(tile.structureIds().size());
        detailsArea.setText(sb.toString());
    }

    public void showAgent(Agent agent) {
        if (agent == null) {
            detailsArea.setText("No agent selected.");
            return;
        }
        titleLabel.setText("Agent: " + agent.name());
        var s = agent.state();
        var p = agent.identity();
        StringBuilder sb = new StringBuilder();
        sb.append("Archetype: ").append(p.archetype()).append("\n");
        sb.append("Location: (").append(s.location().q()).append(", ").append(s.location().r()).append(")\n");
        sb.append(String.format("Credits: %.0f%n", s.credits()));
        sb.append(String.format("Satisfaction: %.2f%n", s.satisfaction()));
        sb.append("Robots: ").append(s.ownedRobots()).append("\n");
        sb.append("Employment: ").append(s.employmentStatus()).append("\n\n");

        sb.append("=== Traits ===\n");
        sb.append(String.format("Risk: %.2f | Ambition: %.2f | Altruism: %.2f%n",
                p.riskTolerance(), p.ambition(), p.altruism()));
        sb.append(String.format("Creativity: %.2f | Compliance: %.2f%n",
                p.creativity(), p.complianceDisposition()));

        sb.append("\n=== Score Vector ===\n");
        var sv = s.scoreVector();
        sb.append(String.format("EF: %.3f | CC: %.3f | LD: %.3f | RC: %.3f%n",
                sv.environmentalFootprint(), sv.commonsContribution(),
                sv.laborDisplacement(), sv.resourceConcentration()));

        sb.append("\n=== Modifiers ===\n");
        var m = s.modifiers();
        sb.append(String.format("EF: %.3f | CC: %.3f | RC: %.3f | LD rate: %.4f%n",
                m.environmentalFootprint(), m.commonsContribution(),
                m.resourceConcentration(), m.laborDisplacementRate()));
        sb.append(String.format("Combined: %.4f%n", m.combinedMultiplier()));

        sb.append("\n=== Inventory ===\n");
        s.inventory().forEach((item, qty) -> sb.append("  ").append(item.id()).append(": ").append(qty).append("\n"));

        sb.append("\n=== Memory (recent) ===\n");
        for (var mem : agent.memory().getRecent(5)) {
            sb.append(String.format("  [%d] %s: %s%n", mem.tick(), mem.type(), mem.description()));
        }

        detailsArea.setText(sb.toString());
    }
}
