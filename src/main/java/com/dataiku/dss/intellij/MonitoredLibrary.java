package com.dataiku.dss.intellij;

import com.dataiku.dss.model.metadata.DssLibraryMetadata;
import com.intellij.openapi.vfs.VirtualFile;

public class MonitoredLibrary extends MonitoredFileSystem{
    public final DssLibraryMetadata library;

    public MonitoredLibrary(VirtualFile libraryBaseDir, MetadataFile metadataFile, DssLibraryMetadata library) {
        super(libraryBaseDir, metadataFile, library);
        this.library = library;
    }

}
