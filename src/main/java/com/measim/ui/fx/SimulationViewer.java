package com.measim.ui.fx;

import com.measim.dao.AgentDao;
import com.measim.dao.CommunicationDao;
import com.measim.dao.MetricsDao;
import com.measim.dao.WorldDao;
import com.measim.model.agent.Agent;
import com.measim.model.world.HexCoord;
import com.measim.model.world.Tile;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class SimulationViewer extends Application {

    private static WorldDao worldDao;
    private static AgentDao agentDao;
    private static MetricsDao metricsDao;
    private static CommunicationDao communicationDao;

    private HexRenderer hexRenderer;
    private InspectorPanel inspectorPanel;
    private DashboardPanel dashboardPanel;
    private CommunicationPanel communicationPanel;
    private LiveConsolePanel liveConsolePanel;
    private Canvas canvas;

    public static void setDependencies(WorldDao world, AgentDao agent, MetricsDao metrics) {
        worldDao = world;
        agentDao = agent;
        metricsDao = metrics;
    }

    public static void setCommunicationDao(CommunicationDao comms) {
        communicationDao = comms;
    }

    @Override
    public void start(Stage primaryStage) {
        hexRenderer = new HexRenderer();
        inspectorPanel = new InspectorPanel();
        dashboardPanel = new DashboardPanel();
        communicationPanel = new CommunicationPanel();
        liveConsolePanel = new LiveConsolePanel();

        canvas = new Canvas(800, 600);

        Pane canvasHolder = new Pane(canvas);
        canvas.widthProperty().bind(canvasHolder.widthProperty());
        canvas.heightProperty().bind(canvasHolder.heightProperty());
        canvas.widthProperty().addListener((obs, o, n) -> redraw());
        canvas.heightProperty().addListener((obs, o, n) -> redraw());

        // Right panel: tabs for Inspector and Communication
        TabPane rightTabs = new TabPane();
        Tab inspectorTab = new Tab("Inspector", inspectorPanel);
        inspectorTab.setClosable(false);
        Tab commsTab = new Tab("Communication Log", communicationPanel);
        commsTab.setClosable(false);
        rightTabs.getTabs().addAll(inspectorTab, commsTab);
        rightTabs.setPrefWidth(420);

        // Wire filter/search to update
        communicationPanel.getChannelFilter().setOnAction(e -> updateComms());
        communicationPanel.getSearchField().textProperty().addListener((obs, o, n) -> updateComms());

        // Main layout: map in center, panels on sides, console at bottom
        javafx.scene.control.SplitPane centerSplit = new javafx.scene.control.SplitPane();
        centerSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        centerSplit.getItems().addAll(canvasHolder, liveConsolePanel);
        centerSplit.setDividerPositions(0.75);

        BorderPane root = new BorderPane();
        root.setCenter(centerSplit);
        root.setRight(rightTabs);
        root.setLeft(dashboardPanel);

        // Mouse interactions
        canvas.setOnMouseClicked(e -> {
            HexCoord coord = hexRenderer.pixelToHex(e.getX(), e.getY());
            if (worldDao != null) {
                Tile tile = worldDao.getTile(coord);
                inspectorPanel.showTile(tile);
                if (agentDao != null) {
                    for (Agent agent : agentDao.getAllAgents()) {
                        if (agent.state().location().equals(coord)) {
                            inspectorPanel.showAgent(agent);
                            break;
                        }
                    }
                }
            }
            rightTabs.getSelectionModel().select(inspectorTab);
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

        Scene scene = new Scene(root, 1500, 900);
        primaryStage.setTitle("MeaSim — New World Order Simulation");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> liveConsolePanel.restoreStdout());
        primaryStage.show();

        redraw();
        if (metricsDao != null) dashboardPanel.update(metricsDao.getHistory());
        updateComms();
    }

    private void redraw() {
        if (worldDao != null && worldDao.getGrid() != null) {
            var agents = agentDao != null ? agentDao.getAllAgents() : java.util.List.<com.measim.model.agent.Agent>of();
            hexRenderer.render(canvas.getGraphicsContext2D(), worldDao.getGrid(),
                    HexRenderer.RenderOptions.withAgents(agents));
        }
    }

    private void updateComms() {
        if (communicationDao != null) {
            communicationPanel.update(communicationDao.getAllMessages());
        }
    }
}
