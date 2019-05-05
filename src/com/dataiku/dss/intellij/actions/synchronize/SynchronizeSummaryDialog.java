package com.dataiku.dss.intellij.actions.synchronize;

import java.awt.*;
import java.util.List;
import javax.swing.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.dataiku.dss.intellij.SynchronizeSummary;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;

public class SynchronizeSummaryDialog extends DialogWrapper {

    private final SynchronizeSummary summary;

    public SynchronizeSummaryDialog(@Nullable Project project, SynchronizeSummary summary) {
        super(project);
        this.summary = summary;
        setTitle("Synchronization Summary");
        setOKButtonText("Close");
        init();
    }

    @NotNull
    @Override
    protected Action[] createActions() {
        return new Action[]{this.getOKAction()};
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        Font font = new JLabel().getFont();
        if (font == null) {
            font = (Font) UIManager.get("Label.font");
        }
        JTextArea summaryTextArea = new JTextArea();
        summaryTextArea.setEditable(false);
        summaryTextArea.setFont(font);

        JScrollPane scrollPane = new JBScrollPane(summaryTextArea);
        scrollPane.setPreferredSize(new Dimension(720, 480));
        scrollPane.setMinimumSize(new Dimension(320, 200));

        summaryTextArea.setText(generateSummaryText(summary));
        summaryTextArea.setCaretPosition(0);

        return scrollPane;
    }

    @VisibleForTesting
    static String generateSummaryText(SynchronizeSummary summary) {
        StringBuilder sb = new StringBuilder();
        if (!summary.isEmpty()) {
            printSummary(sb, "Files locally updated:", summary.locallyUpdated);
            printSummary(sb, "Files locally deleted:", summary.locallyDeleted);
            printSummary(sb, "Files uploaded to DSS instance:", summary.dssUpdated);
            printSummary(sb, "Files deleted from DSS instance:", summary.dssDeleted);
            printSummary(sb, "Conflicted files:", summary.conflicts);
        } else {
            sb.append("No change detected.");
        }
        return sb.toString();
    }

    private static void printSummary(StringBuilder sb, String title, List<String> items) {
        if (!items.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(title);
            for (String s : items) {
                sb.append("\n - ").append(s);
            }
        }
    }
}
