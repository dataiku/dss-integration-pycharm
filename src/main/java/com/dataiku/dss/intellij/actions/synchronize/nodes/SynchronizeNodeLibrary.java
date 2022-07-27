package com.dataiku.dss.intellij.actions.synchronize.nodes;

import com.dataiku.dss.intellij.MonitoredLibrary;

import java.util.List;

public class SynchronizeNodeLibrary extends SynchronizeBaseNode {
    public final MonitoredLibrary monitoredLibrary;

    public SynchronizeNodeLibrary(MonitoredLibrary monitoredLibrary) {
        this.monitoredLibrary = monitoredLibrary;
    }

    public List<SynchronizeNodeLibraryContent> getContentNodes() {
        return listChildren(SynchronizeNodeLibraryContent.class);
    }

    @Override
    public String toString() {
        return monitoredLibrary.library.projectKey;
    }

}
