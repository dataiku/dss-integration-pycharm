package com.dataiku.dss.intellij.actions.synchronize;

import java.util.ArrayList;
import java.util.List;

class SynchronizeSummary {
    List<String> locallyDeleted = new ArrayList<>();
    List<String> locallyUpdated = new ArrayList<>();
    List<String> dssUpdated = new ArrayList<>();
    List<String> dssDeleted = new ArrayList<>();
    List<String> conflicts = new ArrayList<>();

    boolean isEmpty() {
        return locallyUpdated.isEmpty()
                && locallyDeleted.isEmpty()
                && dssUpdated.isEmpty()
                && dssDeleted.isEmpty()
                && conflicts.isEmpty();
    }


    String getQuickSummary() {
        StringBuilder sb = new StringBuilder();
        if (!isEmpty()) {
            quickSummaryLine(sb, locallyUpdated.size(), "locally updated");
            quickSummaryLine(sb, locallyDeleted.size(), "locally deleted");
            quickSummaryLine(sb, dssUpdated.size(), "saved to DSS");
            quickSummaryLine(sb, dssDeleted.size(), "deleted from DSS");
            quickSummaryLine(sb, conflicts.size(), "in conflict");
            sb.append("<a href=\"viewSummary\">Show Details</a>");
        } else {
            sb.append("No change detected.");
        }
        return sb.toString();
    }

    private void quickSummaryLine(StringBuilder sb, int size, final String state) {
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
