package com.dataiku.dss.intellij.actions.synchronize.nodes;

import com.dataiku.dss.model.metadata.DssLibraryFileMetadata;

import java.util.List;

public class SynchronizeNodeLibraryContent extends SynchronizeBaseNode {
    public final DssLibraryFileMetadata libraryFile;
    public final String name;

    public SynchronizeNodeLibraryContent(DssLibraryFileMetadata libraryFile, String name) {
        this.libraryFile = libraryFile;
        this.name = name;
    }

    public List<SynchronizeNodeLibraryContent> getContentNodes() {
        return listChildren(SynchronizeNodeLibraryContent.class);
    }

    @Override
    public String toString() {
        return name;
    }
}
