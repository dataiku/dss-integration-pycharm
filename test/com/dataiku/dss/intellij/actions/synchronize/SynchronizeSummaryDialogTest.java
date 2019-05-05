package com.dataiku.dss.intellij.actions.synchronize;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.dataiku.dss.intellij.SynchronizeSummary;

public class SynchronizeSummaryDialogTest {
    @Test
    public void testGenerateSummaryText() throws Exception {
        SynchronizeSummary summary = new SynchronizeSummary();
        summary.locallyUpdated.add("item1");
        summary.locallyUpdated.add("item2");
        summary.dssDeleted.add("item3");
        String expected = "Files locally updated:\n" +
                " - item1\n" +
                " - item2\n" +
                "\n" +
                "Files deleted from DSS instance:\n" +
                " - item3";
        assertEquals(expected, SynchronizeSummaryDialog.generateSummaryText(summary));
    }

}