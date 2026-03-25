package com.measim.ui.fx;

import com.measim.model.agent.AgentAction;
import com.measim.service.llm.LlmResponseParser;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Player control panel — shows agent context and accepts action input.
 * When the player agent's turn comes, this panel activates and the
 * simulation waits for the player to submit their action(s).
 */
public class PlayerPanel extends VBox {

    private final TextArea contextArea = new TextArea();
    private final TextArea actionInput = new TextArea();
    private final Label statusLabel = new Label("Waiting for simulation...");
    private final Button submitBtn = new Button("Submit Actions");
    private final Button idleBtn = new Button("Pass (Idle)");

    private volatile CountDownLatch actionLatch;
    private final AtomicReference<List<AgentAction>> submittedActions = new AtomicReference<>(null);

    public PlayerPanel() {
        setSpacing(5);
        setPadding(new Insets(5));

        Label title = new Label("Player Agent Control");
        title.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        statusLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #666;");

        contextArea.setEditable(false);
        contextArea.setWrapText(true);
        contextArea.setPrefHeight(300);
        contextArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11;");
        contextArea.setPromptText("Agent context will appear here when it's your turn...");

        Label actionLabel = new Label("Your action(s) — JSON format:");
        actionLabel.setStyle("-fx-font-size: 11;");

        actionInput.setWrapText(true);
        actionInput.setPrefHeight(100);
        actionInput.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11;");
        actionInput.setPromptText("{\"action\": \"SEND_MESSAGE\", \"targetAgent\": \"agent_42\", \"message\": \"Hello!\"}");
        actionInput.setDisable(true);

        submitBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 12;");
        submitBtn.setDisable(true);
        submitBtn.setOnAction(e -> submitAction());

        idleBtn.setStyle("-fx-font-size: 11;");
        idleBtn.setDisable(true);
        idleBtn.setOnAction(e -> {
            submittedActions.set(List.of(new AgentAction.Idle()));
            actionInput.setDisable(true);
            submitBtn.setDisable(true);
            idleBtn.setDisable(true);
            statusLabel.setText("Passed. Waiting for next turn...");
            if (actionLatch != null) actionLatch.countDown();
        });

        // Quick action buttons
        HBox quickActions = new HBox(5);
        quickActions.getChildren().addAll(
                createQuickButton("Broadcast", "{\"action\": \"BROADCAST\", \"message\": \"\"}"),
                createQuickButton("Send Msg", "{\"action\": \"SEND_MESSAGE\", \"targetAgent\": \"\", \"message\": \"\"}"),
                createQuickButton("Offer Trade", "{\"action\": \"OFFER_TRADE\", \"targetAgent\": \"\", \"itemsOffered\": {}, \"itemsRequested\": {}, \"creditsOffered\": 0, \"creditsRequested\": 0, \"message\": \"\"}"),
                createQuickButton("Move", "{\"action\": \"MOVE\", \"q\": 0, \"r\": 0}"),
                createQuickButton("Claim Property", "{\"action\": \"CLAIM_PROPERTY\", \"q\": 0, \"r\": 0}"),
                createQuickButton("Build Infra", "{\"action\": \"BUILD_INFRASTRUCTURE\", \"name\": \"\", \"description\": \"\"}")
        );

        HBox buttons = new HBox(5, submitBtn, idleBtn);

        VBox.setVgrow(contextArea, Priority.ALWAYS);

        getChildren().addAll(title, statusLabel, contextArea, actionLabel, quickActions, actionInput, buttons);
    }

    private Button createQuickButton(String label, String template) {
        Button btn = new Button(label);
        btn.setStyle("-fx-font-size: 10;");
        btn.setOnAction(e -> {
            if (!actionInput.isDisable()) {
                String existing = actionInput.getText().trim();
                if (existing.isEmpty()) {
                    actionInput.setText(template);
                } else {
                    // Append as array
                    if (existing.startsWith("[")) {
                        actionInput.setText(existing.substring(0, existing.length() - 1) + ", " + template + "]");
                    } else {
                        actionInput.setText("[" + existing + ", " + template + "]");
                    }
                }
            }
        });
        return btn;
    }

    /**
     * Called by the simulation when it's the player's turn.
     * Shows context and blocks until the player submits actions.
     */
    public List<AgentAction> waitForPlayerAction(String context, String tradeContext) {
        actionLatch = new CountDownLatch(1);
        submittedActions.set(null);

        Platform.runLater(() -> {
            contextArea.setText(context + "\n\n" + tradeContext);
            actionInput.setText("");
            actionInput.setDisable(false);
            submitBtn.setDisable(false);
            idleBtn.setDisable(false);
            statusLabel.setText("YOUR TURN — enter action(s) and click Submit");
            statusLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #d32f2f; -fx-font-weight: bold;");
        });

        try {
            actionLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of(new AgentAction.Idle());
        }

        List<AgentAction> actions = submittedActions.get();
        return actions != null ? actions : List.of(new AgentAction.Idle());
    }

    private void submitAction() {
        String json = actionInput.getText().trim();
        if (json.isEmpty()) return;

        try {
            List<AgentAction> actions = LlmResponseParser.parseAgentActions(json);
            submittedActions.set(actions);
            actionInput.setDisable(true);
            submitBtn.setDisable(true);
            idleBtn.setDisable(true);
            statusLabel.setText("Actions submitted. Waiting...");
            statusLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #666;");
            if (actionLatch != null) actionLatch.countDown();
        } catch (Exception e) {
            statusLabel.setText("Invalid JSON: " + e.getMessage());
            statusLabel.setStyle("-fx-font-size: 12; -fx-text-fill: red;");
        }
    }
}
