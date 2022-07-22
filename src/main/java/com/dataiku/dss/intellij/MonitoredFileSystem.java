package com.dataiku.dss.intellij;

import com.dataiku.dss.model.metadata.DssFileMetadata;
import com.dataiku.dss.model.metadata.DssFileSystemMetadata;
import com.google.common.base.Preconditions;
import com.intellij.openapi.vfs.VirtualFile;

public class MonitoredFileSystem {
    public final VirtualFile baseDir;
    public final MetadataFile metadataFile;
    public final DssFileSystemMetadata fsMetadata;

    public MonitoredFileSystem(VirtualFile baseDir, MetadataFile metadataFile, DssFileSystemMetadata fsMetadata) {
        Preconditions.checkNotNull(baseDir, "baseDir");
        Preconditions.checkNotNull(metadataFile, "metadataFile");
        Preconditions.checkNotNull(fsMetadata, "fsMetadata");

        this.baseDir = baseDir;
        this.metadataFile = metadataFile;
        this.fsMetadata = fsMetadata;
    }

    public DssFileMetadata findFile(String path) {
        return fsMetadata.findFile(path);
    }

    public void removeFile(String path) {
        fsMetadata.removeFile(path);
    }
}
