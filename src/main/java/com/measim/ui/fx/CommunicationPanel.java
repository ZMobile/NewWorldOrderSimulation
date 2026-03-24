package com.measim.ui.fx;

import com.measim.model.communication.Message;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Communication log with filtering, search, and full message detail.
 * Preserves scroll position on refresh. Ctrl+F focuses search, Enter finds next.
 */
public class CommunicationPanel extends VBox {

    private final ListView<String> messageList = new ListView<>();
    private final TextArea detailArea = new TextArea();
    private final ComboBox<String> channelFilter = new ComboBox<>();
    private final ComboBox<String> agentFilter = new ComboBox<>();
    private final TextField searchField = new TextField();
    private final Label statusLabel = new Label("0 messages");
    private final CheckBox autoScrollCheck = new CheckBox("Auto-scroll");
    private List<Message> allMessages = new ArrayList<>();
    private int searchIndex = -1; // current search match position

    public CommunicationPanel() {
        setSpacing(3);

        Label title = new Label("Communication Log");
        title.setStyle("-fx-font-size: 13; -fx-font-weight: bold;");

        channelFilter.getItems().addAll("ALL", "AGENT_TO_AGENT", "AGENT_TO_GM", "GM_TO_AGENT",
                "GM_INTERNAL", "AGENT_INTERNAL", "BROADCAST", "GM_WORLD_NARRATION");
        channelFilter.setValue("ALL");
        channelFilter.setPrefWidth(150);

        agentFilter.getItems().addAll("All", "All Agents (no GM)");
        agentFilter.setValue("All");
        agentFilter.setPrefWidth(150);

        searchField.setPromptText("Search (Ctrl+F, Enter=next)");
        searchField.setPrefWidth(200);

        autoScrollCheck.setSelected(false);
        autoScrollCheck.setStyle("-fx-font-size: 11;");

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
        HBox buttons = new HBox(3, refreshBtn, copyAllBtn, autoScrollCheck, statusLabel);

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
        channelFilter.setOnAction(e -> { searchIndex = -1; refreshView(); });
        agentFilter.setOnAction(e -> { searchIndex = -1; refreshView(); });

        // Search: Enter = find next match
        searchField.setOnAction(e -> findNext());
        searchField.textProperty().addListener((obs, o, n) -> {
            searchIndex = -1; // reset on text change
            refreshView();
        });

        // Ctrl+F focuses search field
        this.setOnKeyPressed(e -> {
            if (new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN).match(e)) {
                searchField.requestFocus();
                searchField.selectAll();
                e.consume();
            }
        });

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
        agentFilter.getItems().add("All");
        agentFilter.getItems().add("All Agents (no GM)");
        agentFilter.getItems().add("GAME_MASTER");
        agentFilter.getItems().addAll(agents);
        agentFilter.setValue(currentAgent != null && agentFilter.getItems().contains(currentAgent)
                ? currentAgent : "All");

        refreshView();
    }

    private void refreshView() {
        // Remember selection
        int selectedIdx = messageList.getSelectionModel().getSelectedIndex();

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

        // Only auto-scroll to bottom if checkbox is on
        if (autoScrollCheck.isSelected() && !messageList.getItems().isEmpty()) {
            messageList.scrollTo(messageList.getItems().size() - 1);
        } else if (selectedIdx >= 0 && selectedIdx < messageList.getItems().size()) {
            // Restore previous selection
            messageList.getSelectionModel().select(selectedIdx);
        }
    }

    /** Find next search match and scroll to it. */
    private void findNext() {
        String search = searchField.getText().toLowerCase().trim();
        if (search.isEmpty()) return;

        var items = messageList.getItems();
        int start = searchIndex + 1;

        // Search forward from current position, wrap around
        for (int i = 0; i < items.size(); i++) {
            int idx = (start + i) % items.size();
            if (items.get(idx).toLowerCase().contains(search)) {
                searchIndex = idx;
                messageList.getSelectionModel().select(idx);
                messageList.scrollTo(idx);

                // Also show full detail
                var filtered = getFilteredMessages();
                if (idx < filtered.size()) {
                    detailArea.setText(formatMessageFull(filtered.get(idx)));
                }
                return;
            }
        }
        // No match found
        statusLabel.setText("No match for: " + search);
    }

    private List<Message> getFilteredMessages() {
        String channel = channelFilter.getValue();
        String agent = agentFilter.getValue();
        String search = searchField.getText().toLowerCase().trim();

        return allMessages.stream()
                .filter(m -> "ALL".equals(channel) || m.channel().name().equals(channel))
                .filter(m -> "All".equals(agent)
                        || ("All Agents (no GM)".equals(agent)
                            && !m.senderId().equals("GAME_MASTER")
                            && !m.channel().name().equals("GM_INTERNAL")
                            && !m.channel().name().equals("GM_TO_AGENT")
                            && !m.channel().name().equals("GM_WORLD_NARRATION"))
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
