package com.measim.ui.fx;

import com.measim.model.agent.Agent;
import com.measim.model.agent.Archetype;
import com.measim.model.world.*;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders hex tiles to a JavaFX Canvas using flat-top hexagons.
 */
public class HexRenderer {

    private static final double HEX_SIZE = 12.0;

    private double offsetX = 0;
    private double offsetY = 0;
    private double zoom = 1.0;

    public void render(GraphicsContext gc, HexGrid grid, RenderOptions options) {
        gc.clearRect(0, 0, gc.getCanvas().getWidth(), gc.getCanvas().getHeight());
        gc.save();
        gc.translate(offsetX, offsetY);
        gc.scale(zoom, zoom);

        for (Tile tile : grid.getAllTiles()) {
            double px = tile.coord().toPixelX(HEX_SIZE);
            double py = tile.coord().toPixelY(HEX_SIZE);

            // Terrain layer
            gc.setFill(terrainColor(tile.terrain()));
            fillHex(gc, px, py, HEX_SIZE);
            gc.setStroke(Color.gray(0.3, 0.2));
            gc.setLineWidth(0.5);
            strokeHex(gc, px, py, HEX_SIZE);

            // Environment overlay
            if (options.showEnvironment()) {
                double health = tile.environment().averageHealth();
                Color overlay = health > 0.7 ? Color.rgb(0, 200, 0, 0.15)
                        : health > 0.4 ? Color.rgb(200, 200, 0, 0.2)
                        : Color.rgb(200, 0, 0, 0.25);
                gc.setFill(overlay);
                fillHex(gc, px, py, HEX_SIZE);
            }

            // Resource indicators
            if (options.showResources() && !tile.resources().isEmpty()) {
                gc.setFill(Color.WHITE);
                gc.fillOval(px - 2, py - 2, 4, 4);
            }

            // Settlement zone marker
            if (tile.isSettlementZone()) {
                gc.setStroke(Color.GOLD);
                gc.setLineWidth(1.5);
                strokeHex(gc, px, py, HEX_SIZE * 0.85);
            }
        }

        // Agent layer (if agents provided)
        if (options.agents() != null && options.showAgents()) {
            // Count agents per tile for density rendering
            Map<HexCoord, Integer> agentCounts = new HashMap<>();
            for (Agent agent : options.agents()) {
                agentCounts.merge(agent.state().location(), 1, Integer::sum);
            }
            for (var entry : agentCounts.entrySet()) {
                HexCoord coord = entry.getKey();
                int count = entry.getValue();
                if (!grid.inBounds(coord)) continue;
                double px = coord.toPixelX(HEX_SIZE);
                double py = coord.toPixelY(HEX_SIZE);

                // Size based on agent count
                double dotSize = Math.min(HEX_SIZE * 0.8, 3 + count * 0.5);
                gc.setFill(Color.rgb(255, 100, 50, 0.8));
                gc.fillOval(px - dotSize / 2, py - dotSize / 2, dotSize, dotSize);

                // Show count if multiple agents
                if (count > 1) {
                    gc.setFill(Color.WHITE);
                    gc.fillText(String.valueOf(count), px - 3, py + 3);
                }
            }
        }

        gc.restore();
    }

    public void pan(double dx, double dy) {
        offsetX += dx;
        offsetY += dy;
    }

    public void zoom(double factor, double pivotX, double pivotY) {
        double oldZoom = zoom;
        zoom = Math.max(0.1, Math.min(5.0, zoom * factor));
        offsetX = pivotX - (pivotX - offsetX) * zoom / oldZoom;
        offsetY = pivotY - (pivotY - offsetY) * zoom / oldZoom;
    }

    public HexCoord pixelToHex(double px, double py) {
        double adjustedX = (px - offsetX) / zoom;
        double adjustedY = (py - offsetY) / zoom;
        double q = (Math.sqrt(3.0) / 3.0 * adjustedX - 1.0 / 3.0 * adjustedY) / HEX_SIZE;
        double r = (2.0 / 3.0 * adjustedY) / HEX_SIZE;
        return hexRound(q, r);
    }

    private Color terrainColor(TerrainType terrain) {
        return switch (terrain) {
            case GRASSLAND -> Color.rgb(120, 180, 80);
            case MOUNTAIN -> Color.rgb(140, 130, 120);
            case DESERT -> Color.rgb(210, 190, 140);
            case WATER -> Color.rgb(70, 130, 200);
            case FOREST -> Color.rgb(50, 120, 50);
            case TUNDRA -> Color.rgb(220, 225, 230);
            case WETLAND -> Color.rgb(80, 160, 150);
        };
    }

    private void fillHex(GraphicsContext gc, double cx, double cy, double size) {
        double[] xPoints = new double[6];
        double[] yPoints = new double[6];
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60 * i - 30);
            xPoints[i] = cx + size * Math.cos(angle);
            yPoints[i] = cy + size * Math.sin(angle);
        }
        gc.fillPolygon(xPoints, yPoints, 6);
    }

    private void strokeHex(GraphicsContext gc, double cx, double cy, double size) {
        double[] xPoints = new double[6];
        double[] yPoints = new double[6];
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60 * i - 30);
            xPoints[i] = cx + size * Math.cos(angle);
            yPoints[i] = cy + size * Math.sin(angle);
        }
        gc.strokePolygon(xPoints, yPoints, 6);
    }

    private HexCoord hexRound(double q, double r) {
        double s = -q - r;
        int rq = (int) Math.round(q);
        int rr = (int) Math.round(r);
        int rs = (int) Math.round(s);
        double dq = Math.abs(rq - q);
        double dr = Math.abs(rr - r);
        double ds = Math.abs(rs - s);
        if (dq > dr && dq > ds) rq = -rr - rs;
        else if (dr > ds) rr = -rq - rs;
        return new HexCoord(rq, rr);
    }

    public record RenderOptions(boolean showEnvironment, boolean showResources,
                                boolean showAgents, boolean showStructures,
                                List<Agent> agents) {
        public static RenderOptions defaults() {
            return new RenderOptions(true, true, true, true, null);
        }
        public static RenderOptions basic() {
            return new RenderOptions(true, true, false, false, null);
        }
        public static RenderOptions withAgents(List<Agent> agents) {
            return new RenderOptions(true, true, true, true, agents);
        }
    }
}
