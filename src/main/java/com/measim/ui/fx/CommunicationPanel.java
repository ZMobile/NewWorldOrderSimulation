package com.measim.ui.fx;

import com.measim.model.communication.Message;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Communication log with threaded view, filtering, and full message detail.
 * Shows conversations grouped by sender, with full content on click.
 */
public class CommunicationPanel extends VBox {

    private final ListView<String> messageList = new ListView<>();
    private final TextArea detailArea = new TextArea();
    private final ComboBox<String> channelFilter = new ComboBox<>();
    private final ComboBox<String> agentFilter = new ComboBox<>();
    private final TextField searchField = new TextField();
    private final Label statusLabel = new Label("0 messages");
    private List<Message> allMessages = new ArrayList<>();

    public CommunicationPanel() {
        setSpacing(3);

        Label title = new Label("Communication Log");
        title.setStyle("-fx-font-size: 13; -fx-font-weight: bold;");

        channelFilter.getItems().addAll("ALL", "AGENT_TO_AGENT", "AGENT_TO_GM", "GM_TO_AGENT",
                "GM_INTERNAL", "AGENT_INTERNAL", "BROADCAST", "GM_WORLD_NARRATION");
        channelFilter.setValue("ALL");
        channelFilter.setPrefWidth(150);

        agentFilter.getItems().add("All Agents");
        agentFilter.setValue("All Agents");
        agentFilter.setPrefWidth(150);

        searchField.setPromptText("Search...");
        searchField.setPrefWidth(150);

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setStyle("-fx-font-size: 11;");

        Button copyAllBtn = new Button("Copy All");
        copyAllBtn.setStyle("-fx-font-size: 11;");
        copyAllBtn.setOnAction(e -> {
            StringBuilder sb = new StringBuilder();
            for (Message msg : getFilteredMessages()) {
                sb.append(formatMessageFull(msg)).append("\n\n");
            }
            copyToClipboard(sb.toString());
        });

        HBox filters = new HBox(3, channelFilter, agentFilter, searchField);
        HBox buttons = new HBox(3, refreshBtn, copyAllBtn, statusLabel);

        messageList.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 10;");
        messageList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        VBox.setVgrow(messageList, Priority.ALWAYS);

        // Click a message to see full content
        messageList.getSelectionModel().selectedIndexProperty().addListener((obs, oldIdx, newIdx) -> {
            int idx = newIdx.intValue();
            var filtered = getFilteredMessages();
            if (idx >= 0 && idx < filtered.size()) {
                detailArea.setText(formatMessageFull(filtered.get(idx)));
            }
        });

        detailArea.setEditable(false);
        detailArea.setWrapText(true);
        detailArea.setPrefHeight(120);
        detailArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11;");

        // Wire refresh
        refreshBtn.setOnAction(e -> refreshView());
        channelFilter.setOnAction(e -> refreshView());
        agentFilter.setOnAction(e -> refreshView());
        searchField.textProperty().addListener((obs, o, n) -> refreshView());

        getChildren().addAll(title, filters, buttons, messageList, detailArea);
    }

    public void update(List<Message> messages) {
        this.allMessages = new ArrayList<>(messages);

        // Update agent filter with unique senders
        Set<String> agents = new TreeSet<>();
        for (Message msg : messages) {
            if (!msg.senderId().equals("GAME_MASTER") && !msg.senderId().equals("ALL")) {
                agents.add(msg.senderId());
            }
        }
        String currentAgent = agentFilter.getValue();
        agentFilter.getItems().clear();
        agentFilter.getItems().add("All Agents");
        agentFilter.getItems().add("GAME_MASTER");
        agentFilter.getItems().addAll(agents);
        agentFilter.setValue(currentAgent != null && agentFilter.getItems().contains(currentAgent)
                ? currentAgent : "All Agents");

        refreshView();
    }

    private void refreshView() {
        var filtered = getFilteredMessages();
        messageList.getItems().clear();

        for (Message msg : filtered) {
            String prefix = switch (msg.channel()) {
                case AGENT_INTERNAL -> "[THOUGHT]";
                case GM_INTERNAL -> "[GM]";
                case AGENT_TO_GM -> "[->GM]";
                case GM_TO_AGENT -> "[GM->]";
                case AGENT_TO_AGENT -> "[CHAT]";
                case BROADCAST -> "[ALL]";
                case GM_WORLD_NARRATION -> "[WORLD]";
            };

            String preview = msg.content().length() > 80
                    ? msg.content().substring(0, 80) + "..."
                    : msg.content();
            messageList.getItems().add(String.format("T%d %s %s: %s",
                    msg.tick(), prefix, msg.senderId(), preview));
        }

        statusLabel.setText(filtered.size() + " messages");

        if (!messageList.getItems().isEmpty()) {
            messageList.scrollTo(messageList.getItems().size() - 1);
        }
    }

    private List<Message> getFilteredMessages() {
        String channel = channelFilter.getValue();
        String agent = agentFilter.getValue();
        String search = searchField.getText().toLowerCase().trim();

        return allMessages.stream()
                .filter(m -> "ALL".equals(channel) || m.channel().name().equals(channel))
                .filter(m -> "All Agents".equals(agent)
                        || m.senderId().equals(agent) || m.receiverId().equals(agent))
                .filter(m -> search.isEmpty()
                        || m.content().toLowerCase().contains(search)
                        || m.senderId().toLowerCase().contains(search))
                .collect(Collectors.toList());
    }

    private String formatMessageFull(Message msg) {
        return String.format("[Tick %d] %s\nFrom: %s -> %s\nChannel: %s | Type: %s\n\n%s",
                msg.tick(), msg.channel(), msg.senderId(), msg.receiverId(),
                msg.channel(), msg.type(), msg.content());
    }

    public ComboBox<String> getChannelFilter() { return channelFilter; }
    public TextField getSearchField() { return searchField; }

    private void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }
}
