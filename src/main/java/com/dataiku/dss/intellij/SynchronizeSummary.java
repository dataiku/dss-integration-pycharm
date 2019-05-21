package com.dataiku.dss.intellij;

import java.util.ArrayList;
import java.util.List;

import com.dataiku.dss.intellij.actions.merge.MonitoredFileConflict;

public class SynchronizeSummary {
    public final List<String> locallyDeleted = new ArrayList<>();
    public final List<String> locallyUpdated = new ArrayList<>();
    public final List<String> dssUpdated = new ArrayList<>();
    public final List<String> dssDeleted = new ArrayList<>();
    public final List<String> conflicts = new ArrayList<>();

    public final List<MonitoredFileConflict> fileConflicts = new ArrayList<>();

    public boolean isEmpty() {
        return locallyUpdated.isEmpty()
                && locallyDeleted.isEmpty()
                && dssUpdated.isEmpty()
                && dssDeleted.isEmpty()
                && conflicts.isEmpty();
    }

    public boolean hasConflicts() {
        return fileConflicts.stream().anyMatch(conflict -> !conflict.resolved);
    }

    public long unresolvedConflictsCount() {
        return fileConflicts.stream().filter(conflict -> !conflict.resolved).count();
    }

    public String getQuickSummary() {
        StringBuilder sb = new StringBuilder();
        if (!isEmpty()) {
            quickSummaryLine(sb, locallyUpdated.size(), "locally updated");
            quickSummaryLine(sb, locallyDeleted.size(), "locally deleted");
            quickSummaryLine(sb, dssUpdated.size(), "saved to DSS");
            quickSummaryLine(sb, dssDeleted.size(), "deleted from DSS");
            long conflictsCount = unresolvedConflictsCount();
            quickSummaryLine(sb, conflictsCount, "in conflict");
            sb.append("<a href=\"viewSummary\">Show Details</a>");
            if (conflictsCount > 0) {
                sb.append(" | <a href=\"resolveConflicts\">Resolve Conflicts</a>");
            }
        } else {
            sb.append("No change detected.");
        }
        return sb.toString();
    }

    private void quickSummaryLine(StringBuilder sb, long size, final String state) {
        if (size > 0) {
            sb.append(size)
                    .append(" file").append(size == 1 ? "" : "s")
                    .append(" ")
                    .append(state)
                    .append(".")
                    .append("\n");
        }
    }
}
