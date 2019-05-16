package com.dataiku.dss.intellij;

import java.util.ArrayList;
import java.util.List;

public class SynchronizeSummary {
    public final List<String> locallyDeleted = new ArrayList<>();
    public final List<String> locallyUpdated = new ArrayList<>();
    public final List<String> dssUpdated = new ArrayList<>();
    public final List<String> dssDeleted = new ArrayList<>();
    public final List<String> conflicts = new ArrayList<>();

    public boolean isEmpty() {
        return locallyUpdated.isEmpty()
                && locallyDeleted.isEmpty()
                && dssUpdated.isEmpty()
                && dssDeleted.isEmpty()
                && conflicts.isEmpty();
    }


    public String getQuickSummary() {
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
