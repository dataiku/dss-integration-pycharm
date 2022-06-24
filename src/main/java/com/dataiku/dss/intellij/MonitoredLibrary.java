package com.dataiku.dss.intellij;

import com.dataiku.dss.model.metadata.DssLibraryFileMetadata;
import com.dataiku.dss.model.metadata.DssLibraryMetadata;
import com.google.common.base.Preconditions;
import com.intellij.openapi.vfs.VirtualFile;

public class MonitoredLibrary {
    public final VirtualFile libraryBaseDir;
    public final MetadataFile metadataFile;
    public final DssLibraryMetadata library;

    public MonitoredLibrary(VirtualFile libraryBaseDir, MetadataFile metadataFile, DssLibraryMetadata library) {
        Preconditions.checkNotNull(libraryBaseDir, "libraryBaseDir");
        Preconditions.checkNotNull(metadataFile, "metadataFile");
        Preconditions.checkNotNull(library, "library");

        this.libraryBaseDir = libraryBaseDir;
        this.metadataFile = metadataFile;
        this.library = library;
    }

    public DssLibraryFileMetadata findFile(String path) {
        return library.findFile(path);
    }

    public void removeFile(String path) {
        library.removeFile(path);
    }
}
