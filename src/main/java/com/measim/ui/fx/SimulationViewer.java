package com.measim.ui.fx;

import com.measim.dao.AgentDao;
import com.measim.dao.MetricsDao;
import com.measim.dao.WorldDao;
import com.measim.model.agent.Agent;
import com.measim.model.world.HexCoord;
import com.measim.model.world.Tile;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * JavaFX application for visualizing MeaSim.
 * This is a standalone viewer — the simulation runs headless and this reads state.
 */
public class SimulationViewer extends Application {

    // These must be set before launch() is called
    private static WorldDao worldDao;
    private static AgentDao agentDao;
    private static MetricsDao metricsDao;

    private HexRenderer hexRenderer;
    private InspectorPanel inspectorPanel;
    private DashboardPanel dashboardPanel;
    private Canvas canvas;

    public static void setDependencies(WorldDao world, AgentDao agent, MetricsDao metrics) {
        worldDao = world;
        agentDao = agent;
        metricsDao = metrics;
    }

    @Override
    public void start(Stage primaryStage) {
        hexRenderer = new HexRenderer();
        inspectorPanel = new InspectorPanel();
        dashboardPanel = new DashboardPanel();

        canvas = new Canvas(800, 600);
        Label statusBar = new Label("Click a tile to inspect. Scroll to zoom. Drag to pan.");

        BorderPane root = new BorderPane();
        root.setCenter(canvas);
        root.setRight(inspectorPanel);
        root.setLeft(dashboardPanel);
        root.setBottom(statusBar);

        // Mouse interactions
        canvas.setOnMouseClicked(e -> {
            HexCoord coord = hexRenderer.pixelToHex(e.getX(), e.getY());
            if (worldDao != null) {
                Tile tile = worldDao.getTile(coord);
                inspectorPanel.showTile(tile);
                // Check for agents at this tile
                if (agentDao != null) {
                    for (Agent agent : agentDao.getAllAgents()) {
                        if (agent.state().location().equals(coord)) {
                            inspectorPanel.showAgent(agent);
                            break;
                        }
                    }
                }
            }
        });

        canvas.setOnScroll(e -> {
            double factor = e.getDeltaY() > 0 ? 1.1 : 0.9;
            hexRenderer.zoom(factor, e.getX(), e.getY());
            redraw();
        });

        final double[] dragStart = new double[2];
        canvas.setOnMousePressed(e -> { dragStart[0] = e.getX(); dragStart[1] = e.getY(); });
        canvas.setOnMouseDragged(e -> {
            hexRenderer.pan(e.getX() - dragStart[0], e.getY() - dragStart[1]);
            dragStart[0] = e.getX();
            dragStart[1] = e.getY();
            redraw();
        });

        Scene scene = new Scene(root, 1400, 800);
        primaryStage.setTitle("MeaSim Viewer");
        primaryStage.setScene(scene);
        primaryStage.show();

        redraw();
        if (metricsDao != null) {
            dashboardPanel.update(metricsDao.getHistory());
        }
    }

    private void redraw() {
        if (worldDao != null && worldDao.getGrid() != null) {
            hexRenderer.render(canvas.getGraphicsContext2D(), worldDao.getGrid(),
                    HexRenderer.RenderOptions.defaults());
        }
    }
}
