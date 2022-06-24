package com.dataiku.dss.intellij.actions.synchronize.nodes;

import java.util.List;

public class SynchronizeNodeLibraries extends SynchronizeBaseNode {

    public List<SynchronizeNodeLibrary> getLibraryNodes() {
        return listChildren(SynchronizeNodeLibrary.class);
    }

    @Override
    public String toString() {
        return "Libraries";
    }
}
