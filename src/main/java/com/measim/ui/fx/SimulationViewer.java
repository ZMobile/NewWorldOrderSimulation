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
    private static com.measim.service.llm.BudgetPauseHandler budgetPauseHandler;
    private static com.measim.dao.LlmDao llmDao;
    private static PlayerPanel playerPanel;
    private static boolean playerMode = false;

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

    public static void setBudgetPauseHandler(com.measim.service.llm.BudgetPauseHandler handler) {
        budgetPauseHandler = handler;
    }

    public static void setLlmDao(com.measim.dao.LlmDao dao) {
        llmDao = dao;
    }

    public static void enablePlayerMode() {
        playerMode = true;
    }

    public static PlayerPanel getPlayerPanel() {
        return playerPanel;
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

        if (playerMode) {
            playerPanel = new PlayerPanel();
            Tab playerTab = new Tab("Player", playerPanel);
            playerTab.setClosable(false);
            rightTabs.getTabs().add(0, playerTab); // first tab
            rightTabs.getSelectionModel().select(playerTab);
        }
        rightTabs.setPrefWidth(420);

        // Wire filter/search to update
        communicationPanel.getChannelFilter().setOnAction(e -> updateComms());
        communicationPanel.getSearchField().textProperty().addListener((obs, o, n) -> updateComms());

        // Horizontal split: map | right panel (draggable divider)
        javafx.scene.control.SplitPane horizontalSplit = new javafx.scene.control.SplitPane();
        horizontalSplit.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        horizontalSplit.getItems().addAll(canvasHolder, rightTabs);
        horizontalSplit.setDividerPositions(0.65);

        // Vertical split: (map+right) over console
        javafx.scene.control.SplitPane verticalSplit = new javafx.scene.control.SplitPane();
        verticalSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        verticalSplit.getItems().addAll(horizontalSplit, liveConsolePanel);
        verticalSplit.setDividerPositions(0.72);

        // Status bar with LLM controls
        javafx.scene.control.Button resumeBtn = new javafx.scene.control.Button("Resume LLM");
        resumeBtn.setStyle("-fx-font-size: 11; -fx-background-color: #4CAF50; -fx-text-fill: white;");
        resumeBtn.setVisible(false);
        resumeBtn.setOnAction(e -> {
            if (budgetPauseHandler != null) {
                if (budgetPauseHandler.isPaused()) {
                    budgetPauseHandler.resume();
                } else if (budgetPauseHandler.isSkipMode()) {
                    budgetPauseHandler.exitSkipMode();
                }
                resumeBtn.setVisible(false);
            }
        });
        Label llmStatusLabel = new Label("LLM: Active");
        llmStatusLabel.setStyle("-fx-font-size: 11; -fx-padding: 3;");
        javafx.scene.layout.HBox statusBar = new javafx.scene.layout.HBox(8, llmStatusLabel, resumeBtn);
        statusBar.setStyle("-fx-padding: 3; -fx-background-color: #f0f0f0;");

        BorderPane root = new BorderPane();
        root.setTop(statusBar);
        root.setCenter(verticalSplit);
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
        primaryStage.show();

        redraw();
        if (metricsDao != null) dashboardPanel.update(metricsDao.getHistory());
        updateComms();

        // Auto-refresh every 5 seconds while simulation runs in background
        javafx.animation.Timeline refreshTimer = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(5), e -> {
                    redraw();
                    if (metricsDao != null) dashboardPanel.update(metricsDao.getHistory());
                    updateComms();
                    // Update LLM status + cost
                    String costText = llmDao != null ? String.format(" | Spent: $%.2f (%d calls)",
                            llmDao.totalSpent(), llmDao.totalCalls()) : "";
                    if (budgetPauseHandler != null) {
                        if (budgetPauseHandler.isPaused()) {
                            llmStatusLabel.setText("LLM: PAUSED" + costText);
                            llmStatusLabel.setStyle("-fx-font-size: 11; -fx-padding: 3; -fx-text-fill: red;");
                            resumeBtn.setVisible(true);
                            resumeBtn.setText("Resume LLM");
                        } else if (budgetPauseHandler.isSkipMode()) {
                            llmStatusLabel.setText("LLM: SKIPPED" + costText);
                            llmStatusLabel.setStyle("-fx-font-size: 11; -fx-padding: 3; -fx-text-fill: orange;");
                            resumeBtn.setVisible(true);
                            resumeBtn.setText("Re-enable LLM");
                        } else {
                            llmStatusLabel.setText("LLM: Active" + costText);
                            llmStatusLabel.setStyle("-fx-font-size: 11; -fx-padding: 3; -fx-text-fill: green;");
                            resumeBtn.setVisible(false);
                        }
                    } else if (llmDao != null) {
                        llmStatusLabel.setText("LLM" + costText);
                    }
                }));
        refreshTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
        refreshTimer.play();
        primaryStage.setOnCloseRequest(ev -> {
            refreshTimer.stop();
            liveConsolePanel.restoreStdout();
            javafx.application.Platform.exit();
            System.exit(0);
        });
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
