package com.measim.service.llm;

import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pauses the simulation when the Anthropic API rejects requests due to
 * exhausted account credits. Non-blocking — user can dismiss and inspect
 * the simulation while paused.
 *
 * Three options:
 *   Continue — user added credits, retry the request
 *   Skip — continue in deterministic mode for this session
 *   Leave Paused — dismiss popup, sim stays paused (can resume later via UI)
 */
@Singleton
public class BudgetPauseHandler {

    public enum Resolution { RETRY, SKIP, PAUSED }

    private final AtomicBoolean pauseTriggered = new AtomicBoolean(false);
    private final AtomicBoolean skipMode = new AtomicBoolean(false);
    private final AtomicReference<CountDownLatch> pauseLatch = new AtomicReference<>(null);
    private volatile Resolution lastResolution = Resolution.RETRY;

    /**
     * Called when the API returns a billing/credit error.
     * If skipMode is active (user previously chose Skip), returns false immediately.
     * Otherwise blocks and shows popup.
     * Returns true if user wants to retry, false if skip/paused.
     */
    public boolean pauseForBudget(double totalSpent, double unused) {
        // If user already chose Skip this session, don't show popup again
        if (skipMode.get()) return false;

        // Only first thread triggers popup — others wait for resolution
        if (!pauseTriggered.compareAndSet(false, true)) {
            waitForResolution();
            return lastResolution == Resolution.RETRY;
        }

        System.out.printf("    [API] Credits exhausted after $%.2f spent. Simulation paused.%n", totalSpent);
        System.out.flush();

        CountDownLatch latch = new CountDownLatch(1);
        pauseLatch.set(latch);

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("API Credits Exhausted");
            alert.setHeaderText("Anthropic API credits have run out");
            alert.setContentText(String.format(
                    "Total spent this session: $%.2f\n\n" +
                    "Continue: Add credits at console.anthropic.com, then retry\n" +
                    "Skip: Continue in deterministic mode (no more LLM calls)\n" +
                    "Leave Paused: Dismiss this dialog to inspect the simulation",
                    totalSpent));

            ButtonType continueBtn = new ButtonType("Continue");
            ButtonType skipBtn = new ButtonType("Skip (Deterministic)");
            ButtonType pauseBtn = new ButtonType("Leave Paused");
            alert.getButtonTypes().setAll(continueBtn, skipBtn, pauseBtn);

            var result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == continueBtn) {
                    lastResolution = Resolution.RETRY;
                } else if (result.get() == skipBtn) {
                    lastResolution = Resolution.SKIP;
                    skipMode.set(true); // don't ask again this session
                } else {
                    lastResolution = Resolution.PAUSED;
                }
            } else {
                lastResolution = Resolution.PAUSED; // closed dialog = leave paused
            }

            switch (lastResolution) {
                case RETRY -> System.out.println("    [API] User clicked Continue. Retrying...");
                case SKIP -> System.out.println("    [API] User clicked Skip. Deterministic mode for rest of session.");
                case PAUSED -> System.out.println("    [API] User chose Leave Paused. Inspect simulation, then call resume.");
            }
            System.out.flush();

            latch.countDown();
            pauseTriggered.set(false);
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // If paused, block until resume() is called
        if (lastResolution == Resolution.PAUSED) {
            CountDownLatch resumeLatch = new CountDownLatch(1);
            pauseLatch.set(resumeLatch);
            try {
                resumeLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // After resume, retry
            return true;
        }

        return lastResolution == Resolution.RETRY;
    }

    /** Called from UI to resume a paused simulation. */
    public void resume() {
        CountDownLatch latch = pauseLatch.getAndSet(null);
        if (latch != null) {
            lastResolution = Resolution.RETRY;
            latch.countDown();
            System.out.println("    [API] Simulation resumed by user.");
        }
    }

    /** Check if simulation is currently paused waiting for credits. */
    public boolean isPaused() {
        return pauseLatch.get() != null && lastResolution == Resolution.PAUSED;
    }

    /** Check if user chose to skip LLM for the rest of the session. */
    public boolean isSkipMode() {
        return skipMode.get();
    }

    /** Re-enable LLM calls after user has added credits. Called from UI. */
    public void exitSkipMode() {
        skipMode.set(false);
        System.out.println("    [API] Skip mode disabled. LLM calls will resume.");
    }

    private void waitForResolution() {
        while (pauseTriggered.get()) {
            try { Thread.sleep(100); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
