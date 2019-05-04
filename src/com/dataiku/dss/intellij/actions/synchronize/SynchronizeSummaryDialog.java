package com.dataiku.dss.intellij.actions.synchronize;

import java.awt.*;
import java.util.List;
import javax.swing.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;

public class SynchronizeSummaryDialog extends DialogWrapper {

    private final SynchronizeModel model;

    SynchronizeSummaryDialog(@Nullable Project project, SynchronizeModel model) {
        super(project);
        this.model = model;
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

        summaryTextArea.setText(generateSummaryText());
        summaryTextArea.setCaretPosition(0);

        return scrollPane;
    }

    private String generateSummaryText() {
        StringBuilder sb = new StringBuilder();
        if (!model.summary.isEmpty()) {
            printSummary(sb, "Files locally updated:", model.summary.locallyUpdated);
            printSummary(sb, "Files locally deleted:", model.summary.locallyDeleted);
            printSummary(sb, "Files uploaded to DSS instance:", model.summary.dssUpdated);
            printSummary(sb, "Files deleted from DSS instance:", model.summary.dssDeleted);
            printSummary(sb, "Conflicted files:", model.summary.conflicts);
        } else {
            sb.append("No change detected.");
        }
        return sb.toString();
    }

    private void printSummary(StringBuilder sb, String title, List<String> items) {
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        if (!items.isEmpty()) {
            sb.append(title);
            for (String s : items) {
                sb.append("\n - ").append(s);
            }
        }
    }
}
