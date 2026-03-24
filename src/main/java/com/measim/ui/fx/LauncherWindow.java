package com.measim.ui.fx;

import com.measim.model.config.SimulationConfig;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Launch window with settings panel and start button.
 * Lets user configure simulation before running.
 */
public class LauncherWindow extends Application {

    private static Consumer<LaunchSettings> onLaunch;

    public static void setOnLaunch(Consumer<LaunchSettings> callback) {
        onLaunch = callback;
    }

    public record LaunchSettings(
            int agentCount, int worldWidth, int worldHeight,
            int totalYears, boolean measEnabled, boolean llmEnabled,
            String apiKey, String model, double budgetUsd, long seed
    ) {}

    @Override
    public void start(Stage stage) {
        // Load defaults from config
        SimulationConfig defaults = SimulationConfig.load(Path.of("config/default.yaml"));

        // Title
        Label title = new Label("MeaSim — New World Order Simulation");
        title.setFont(Font.font("System", FontWeight.BOLD, 20));

        Label subtitle = new Label("Configure simulation parameters, then press Start");
        subtitle.setStyle("-fx-text-fill: #666;");

        // World settings
        Label worldHeader = new Label("World");
        worldHeader.setFont(Font.font("System", FontWeight.BOLD, 14));

        Spinner<Integer> worldWidth = new Spinner<>(20, 500, Math.min(defaults.worldWidth(), 50), 10);
        Spinner<Integer> worldHeight = new Spinner<>(20, 500, Math.min(defaults.worldHeight(), 50), 10);
        Spinner<Integer> seed = new Spinner<>(1, 99999, (int) defaults.seed(), 1);
        worldWidth.setEditable(true);
        worldHeight.setEditable(true);
        seed.setEditable(true);

        GridPane worldGrid = new GridPane();
        worldGrid.setHgap(10); worldGrid.setVgap(5);
        worldGrid.addRow(0, new Label("Width:"), worldWidth);
        worldGrid.addRow(1, new Label("Height:"), worldHeight);
        worldGrid.addRow(2, new Label("Seed:"), seed);

        // Agent settings
        Label agentHeader = new Label("Agents");
        agentHeader.setFont(Font.font("System", FontWeight.BOLD, 14));

        Spinner<Integer> agentCount = new Spinner<>(10, 2000, defaults.agentCount(), 50);
        agentCount.setEditable(true);
        Spinner<Integer> totalYears = new Spinner<>(1, 200, defaults.totalYears(), 5);
        totalYears.setEditable(true);

        GridPane agentGrid = new GridPane();
        agentGrid.setHgap(10); agentGrid.setVgap(5);
        agentGrid.addRow(0, new Label("Count:"), agentCount);
        agentGrid.addRow(1, new Label("Years:"), totalYears);

        // MEAS settings
        Label measHeader = new Label("MEAS System");
        measHeader.setFont(Font.font("System", FontWeight.BOLD, 14));

        CheckBox measEnabled = new CheckBox("MEAS scoring enabled");
        measEnabled.setSelected(defaults.measEnabled());
        Label measHint = new Label("Uncheck to run baseline capitalism for comparison");
        measHint.setStyle("-fx-text-fill: #888; -fx-font-size: 11;");

        // LLM settings
        Label llmHeader = new Label("LLM (Claude)");
        llmHeader.setFont(Font.font("System", FontWeight.BOLD, 14));

        CheckBox llmEnabled = new CheckBox("Enable LLM for Game Master + agent decisions");
        llmEnabled.setSelected(defaults.hasApiKey());

        TextField apiKeyField = new TextField(defaults.apiKey());
        apiKeyField.setPromptText("sk-ant-... or leave empty for ANTHROPIC_API_KEY env var");
        apiKeyField.setPrefWidth(350);
        apiKeyField.setDisable(!llmEnabled.isSelected());
        llmEnabled.selectedProperty().addListener((obs, o, n) -> apiKeyField.setDisable(!n));

        ComboBox<String> modelChoice = new ComboBox<>();
        modelChoice.getItems().addAll("claude-sonnet-4-6", "claude-opus-4-6", "claude-haiku-4-5-20251001");
        modelChoice.setValue("claude-sonnet-4-6");

        Spinner<Double> budget = new Spinner<>(1.0, 500.0, 50.0, 10.0);
        budget.setEditable(true);

        GridPane llmGrid = new GridPane();
        llmGrid.setHgap(10); llmGrid.setVgap(5);
        llmGrid.addRow(0, new Label("API Key:"), apiKeyField);
        llmGrid.addRow(1, new Label("Model:"), modelChoice);
        llmGrid.addRow(2, new Label("Budget ($):"), budget);

        // Start button
        Button startButton = new Button("Start Simulation");
        startButton.setFont(Font.font("System", FontWeight.BOLD, 16));
        startButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-padding: 10 30;");
        startButton.setOnAction(e -> {
            String key = apiKeyField.getText().trim();
            if (key.isEmpty()) {
                String envKey = System.getenv("ANTHROPIC_API_KEY");
                if (envKey != null) key = envKey;
            }

            LaunchSettings settings = new LaunchSettings(
                    agentCount.getValue(),
                    worldWidth.getValue(),
                    worldHeight.getValue(),
                    totalYears.getValue(),
                    measEnabled.isSelected(),
                    llmEnabled.isSelected(),
                    key,
                    modelChoice.getValue(),
                    budget.getValue(),
                    (long) seed.getValue()
            );

            stage.close();
            if (onLaunch != null) onLaunch.accept(settings);
        });

        // Layout
        VBox layout = new VBox(15,
                title, subtitle, new Separator(),
                worldHeader, worldGrid, new Separator(),
                agentHeader, agentGrid, new Separator(),
                measHeader, measEnabled, measHint, new Separator(),
                llmHeader, llmEnabled, llmGrid, new Separator(),
                startButton
        );
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.TOP_LEFT);

        ScrollPane scroll = new ScrollPane(layout);
        scroll.setFitToWidth(true);

        Scene scene = new Scene(scroll, 500, 700);
        stage.setTitle("MeaSim Launcher");
        stage.setScene(scene);
        stage.show();
    }
}
