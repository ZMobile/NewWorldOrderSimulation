package com.measim.ui.fx;

import com.measim.model.agent.AgentAction;
import com.measim.model.economy.ItemType;
import com.measim.model.world.HexCoord;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Player control panel — game-style UI for human agents.
 *
 * Standard actions have dedicated buttons with form dialogs.
 * Everything else is natural language typed into a chat box
 * and processed by the GM as a free-form action.
 */
public class PlayerPanel extends VBox {

    private final TextArea contextArea = new TextArea();
    private final TextField chatInput = new TextField();
    private final ListView<String> chatHistory = new ListView<>();
    private final Label statusLabel = new Label("Waiting for simulation...");
    private final HBox actionButtons = new HBox(5);
    private final Label creditsLabel = new Label("Credits: ---");

    private volatile CountDownLatch actionLatch;
    private final AtomicReference<List<AgentAction>> submittedActions = new AtomicReference<>(null);
    private final List<AgentAction> currentTurnActions = new ArrayList<>();

    public PlayerPanel() {
        setSpacing(5);
        setPadding(new Insets(5));

        Label title = new Label("Player Agent");
        title.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        creditsLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold; -fx-text-fill: #2e7d32;");
        statusLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #666;");

        // Context area — shows what you see (spatial, nearby agents, messages)
        contextArea.setEditable(false);
        contextArea.setWrapText(true);
        contextArea.setPrefHeight(200);
        contextArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 10;");
        contextArea.setPromptText("Your surroundings will appear here...");

        // Action buttons row
        actionButtons.setPadding(new Insets(3, 0, 3, 0));
        Button moveBtn = actionBtn("Move", this::showMoveDialog);
        Button claimBtn = actionBtn("Claim Land", this::showClaimDialog);
        Button tradeBtn = actionBtn("Offer Trade", this::showTradeDialog);
        Button buildBtn = actionBtn("Build", this::showBuildDialog);
        Button jobBtn = actionBtn("Offer Job", this::showJobDialog);
        Button robotBtn = actionBtn("Buy Robot", () -> addAction(new AgentAction.PurchaseRobot()));
        Button endTurnBtn = new Button("End Turn");
        endTurnBtn.setStyle("-fx-background-color: #ff9800; -fx-text-fill: white; -fx-font-size: 11;");
        endTurnBtn.setOnAction(e -> submitTurn());

        actionButtons.getChildren().addAll(moveBtn, claimBtn, tradeBtn, buildBtn, jobBtn, robotBtn, endTurnBtn);

        // Chat history — shows conversation log
        chatHistory.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 10;");
        chatHistory.setPrefHeight(150);

        // Chat input — for messages to agents or GM commands
        chatInput.setPromptText("Type a message to an agent or a command to the GM...");
        chatInput.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11;");
        chatInput.setDisable(true);
        chatInput.setOnAction(e -> handleChatInput());

        Label chatLabel = new Label("Chat / GM Commands:");
        chatLabel.setStyle("-fx-font-size: 11; -fx-font-weight: bold;");

        HBox chatBar = new HBox(5);
        Button sendBtn = new Button("Send");
        sendBtn.setStyle("-fx-font-size: 10;");
        sendBtn.setOnAction(e -> handleChatInput());

        ComboBox<String> chatTarget = new ComboBox<>();
        chatTarget.setPromptText("To:");
        chatTarget.setPrefWidth(120);
        chatTarget.setEditable(true);
        chatTarget.getItems().addAll("GM (free-form action)", "Broadcast (all at tile)");
        this.chatTargetCombo = chatTarget;

        HBox.setHgrow(chatInput, Priority.ALWAYS);
        chatBar.getChildren().addAll(chatTarget, chatInput, sendBtn);

        Label turnActionsLabel = new Label("This turn:");
        turnActionsLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #888;");
        turnActionsList = new Label("No actions yet");
        turnActionsList.setStyle("-fx-font-size: 10; -fx-text-fill: #555; -fx-wrap-text: true;");

        VBox.setVgrow(chatHistory, Priority.ALWAYS);

        getChildren().addAll(
                title, new HBox(10, creditsLabel, statusLabel),
                contextArea,
                actionButtons,
                turnActionsLabel, turnActionsList,
                chatLabel, chatHistory, chatBar
        );
    }

    private final ComboBox<String> chatTargetCombo;
    private Label turnActionsList;

    private Button actionBtn(String label, Runnable action) {
        Button btn = new Button(label);
        btn.setStyle("-fx-font-size: 10;");
        btn.setOnAction(e -> {
            if (!chatInput.isDisable()) action.run();
        });
        return btn;
    }

    /**
     * Called by simulation when it's the player's turn.
     * Shows context and enables UI. Blocks until player clicks End Turn.
     */
    public List<AgentAction> waitForPlayerAction(String context, String tradeContext) {
        actionLatch = new CountDownLatch(1);
        submittedActions.set(null);
        currentTurnActions.clear();

        Platform.runLater(() -> {
            contextArea.setText(context);
            if (!tradeContext.equals("None")) {
                chatHistory.getItems().add("[Trade offers] " + tradeContext);
            }

            // Extract credits from context
            String[] lines = context.split("\n");
            for (String line : lines) {
                if (line.contains("Credits:")) {
                    creditsLabel.setText(line.trim());
                    break;
                }
            }

            // Populate chat target with nearby agents
            for (String line : lines) {
                if (line.trim().startsWith("agent_")) {
                    String agentId = line.trim().split("\\s")[0];
                    if (!chatTargetCombo.getItems().contains(agentId)) {
                        chatTargetCombo.getItems().add(agentId);
                    }
                }
            }

            chatInput.setDisable(false);
            turnActionsList.setText("No actions yet");
            statusLabel.setText("YOUR TURN");
            statusLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #d32f2f; -fx-font-weight: bold;");
        });

        try {
            actionLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of(new AgentAction.Idle());
        }

        List<AgentAction> actions = submittedActions.get();
        return actions != null && !actions.isEmpty() ? actions : List.of(new AgentAction.Idle());
    }

    private void addAction(AgentAction action) {
        currentTurnActions.add(action);
        String desc = switch (action) {
            case AgentAction.Move m -> "Move to (" + m.destination().q() + "," + m.destination().r() + ")";
            case AgentAction.ClaimProperty c -> "Claim property at (" + c.tile().q() + "," + c.tile().r() + ")";
            case AgentAction.PurchaseRobot ignored -> "Purchase robot";
            case AgentAction.SendMessage m -> "Message to " + m.targetAgentId();
            case AgentAction.BroadcastMessage b -> "Broadcast: " + b.content().substring(0, Math.min(40, b.content().length()));
            case AgentAction.FreeFormAction f -> "GM: " + f.description().substring(0, Math.min(40, f.description().length()));
            case AgentAction.OfferTrade t -> "Trade offer to " + (t.targetAgentId() != null ? t.targetAgentId() : "open");
            case AgentAction.OfferJob j -> "Job offer to " + j.targetAgentId();
            default -> action.getClass().getSimpleName();
        };
        Platform.runLater(() -> {
            turnActionsList.setText(currentTurnActions.size() + " action(s): " + desc);
            chatHistory.getItems().add("[You] " + desc);
        });
    }

    private void submitTurn() {
        if (currentTurnActions.isEmpty()) {
            currentTurnActions.add(new AgentAction.Idle());
        }
        submittedActions.set(new ArrayList<>(currentTurnActions));
        Platform.runLater(() -> {
            chatInput.setDisable(true);
            statusLabel.setText("Turn submitted. Waiting...");
            statusLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #666;");
        });
        if (actionLatch != null) actionLatch.countDown();
    }

    private void handleChatInput() {
        String text = chatInput.getText().trim();
        if (text.isEmpty()) return;
        chatInput.clear();

        String target = chatTargetCombo.getValue();
        if (target == null || target.isEmpty()) {
            chatHistory.getItems().add("[Error] Select a target first");
            return;
        }

        if (target.equals("GM (free-form action)")) {
            // Parse budget from text if mentioned, default 100
            double budget = 100;
            String lower = text.toLowerCase();
            if (lower.contains("budget")) {
                try {
                    String[] parts = lower.split("budget[:\\s]+");
                    if (parts.length > 1) {
                        budget = Double.parseDouble(parts[1].replaceAll("[^0-9.]", "").trim());
                    }
                } catch (Exception ignored) {}
            }
            addAction(new AgentAction.FreeFormAction(text, budget));
        } else if (target.equals("Broadcast (all at tile)")) {
            addAction(new AgentAction.BroadcastMessage(text));
        } else {
            // Direct message to agent
            addAction(new AgentAction.SendMessage(target, text));
        }
    }

    // ========== Action Dialogs ==========

    private void showMoveDialog() {
        Dialog<HexCoord> dialog = new Dialog<>();
        dialog.setTitle("Move");
        dialog.setHeaderText("Enter destination coordinates");
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        TextField qField = new TextField("0"); qField.setPrefWidth(60);
        TextField rField = new TextField("0"); rField.setPrefWidth(60);
        grid.addRow(0, new Label("Q:"), qField);
        grid.addRow(1, new Label("R:"), rField);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(btn -> btn == ButtonType.OK
                ? new HexCoord(Integer.parseInt(qField.getText()), Integer.parseInt(rField.getText()))
                : null);
        dialog.showAndWait().ifPresent(coord -> addAction(new AgentAction.Move(coord)));
    }

    private void showClaimDialog() {
        Dialog<HexCoord> dialog = new Dialog<>();
        dialog.setTitle("Claim Property");
        dialog.setHeaderText("Claim land at coordinates (must be within 2 tiles)");
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        TextField qField = new TextField("0"); qField.setPrefWidth(60);
        TextField rField = new TextField("0"); rField.setPrefWidth(60);
        grid.addRow(0, new Label("Q:"), qField);
        grid.addRow(1, new Label("R:"), rField);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(btn -> btn == ButtonType.OK
                ? new HexCoord(Integer.parseInt(qField.getText()), Integer.parseInt(rField.getText()))
                : null);
        dialog.showAndWait().ifPresent(coord -> addAction(new AgentAction.ClaimProperty(coord)));
    }

    private void showTradeDialog() {
        Dialog<AgentAction.OfferTrade> dialog = new Dialog<>();
        dialog.setTitle("Offer Trade");
        dialog.setHeaderText("Create a trade offer");
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        TextField targetField = new TextField(); targetField.setPromptText("agent_42 (or blank for open)");
        TextField offerItems = new TextField(); offerItems.setPromptText("TIMBER:3,FOOD:2");
        TextField requestItems = new TextField(); requestItems.setPromptText("MINERAL:1");
        TextField creditsOffer = new TextField("0");
        TextField creditsRequest = new TextField("0");
        TextField message = new TextField(); message.setPromptText("Trade message...");
        grid.addRow(0, new Label("To:"), targetField);
        grid.addRow(1, new Label("Offering:"), offerItems);
        grid.addRow(2, new Label("Requesting:"), requestItems);
        grid.addRow(3, new Label("Credits offering:"), creditsOffer);
        grid.addRow(4, new Label("Credits requesting:"), creditsRequest);
        grid.addRow(5, new Label("Message:"), message);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            String target = targetField.getText().trim().isEmpty() ? null : targetField.getText().trim();
            return new AgentAction.OfferTrade(target,
                    parseItemMap(offerItems.getText()), parseItemMap(requestItems.getText()),
                    parseDouble(creditsOffer.getText()), parseDouble(creditsRequest.getText()),
                    message.getText());
        });
        dialog.showAndWait().ifPresent(this::addAction);
    }

    private void showBuildDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Build Infrastructure");
        dialog.setHeaderText("Describe what you want to build.\nThe GM will evaluate feasibility and return a quote.");
        dialog.setContentText("Description:");
        dialog.getEditor().setPrefWidth(400);
        dialog.showAndWait().ifPresent(desc -> {
            if (!desc.isEmpty()) {
                addAction(new AgentAction.FreeFormAction("Build infrastructure: " + desc, 500));
            }
        });
    }

    private void showJobDialog() {
        Dialog<AgentAction.OfferJob> dialog = new Dialog<>();
        dialog.setTitle("Offer Job");
        dialog.setHeaderText("Offer employment to an agent");
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        TextField targetField = new TextField(); targetField.setPromptText("agent_42");
        TextField wagesField = new TextField("5.0");
        TextField durationField = new TextField("12");
        TextField descField = new TextField(); descField.setPromptText("Job description...");
        grid.addRow(0, new Label("Agent:"), targetField);
        grid.addRow(1, new Label("Wages/tick:"), wagesField);
        grid.addRow(2, new Label("Duration (ticks):"), durationField);
        grid.addRow(3, new Label("Description:"), descField);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(btn -> btn == ButtonType.OK
                ? new AgentAction.OfferJob(targetField.getText().trim(),
                    parseDouble(wagesField.getText()), Integer.parseInt(durationField.getText().trim()),
                    descField.getText())
                : null);
        dialog.showAndWait().ifPresent(this::addAction);
    }

    // ========== Helpers ==========

    private Map<ItemType, Integer> parseItemMap(String text) {
        Map<ItemType, Integer> map = new HashMap<>();
        if (text == null || text.trim().isEmpty()) return map;
        for (String pair : text.split(",")) {
            String[] parts = pair.trim().split(":");
            if (parts.length == 2) {
                map.put(ItemType.custom(parts[0].trim()), Integer.parseInt(parts[1].trim()));
            }
        }
        return map;
    }

    private double parseDouble(String text) {
        try { return Double.parseDouble(text.trim()); }
        catch (Exception e) { return 0; }
    }
}
