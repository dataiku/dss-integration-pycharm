package com.dataiku.dss.intellij.actions.merge;

import com.dataiku.dss.intellij.MonitoredLibrary;
import com.dataiku.dss.model.metadata.DssLibraryFileMetadata;
import com.intellij.openapi.vfs.VirtualFile;

public class MonitoredLibraryFileConflict extends MonitoredFileConflict {
    public MonitoredLibrary library;
    public DssLibraryFileMetadata libraryFile;

    public MonitoredLibraryFileConflict(VirtualFile file, MonitoredLibrary library, DssLibraryFileMetadata libraryFile) {
        super(file);
        this.library = library;
        this.libraryFile = libraryFile;
    }
}
