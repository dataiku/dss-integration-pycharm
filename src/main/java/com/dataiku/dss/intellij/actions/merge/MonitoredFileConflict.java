package com.dataiku.dss.intellij.actions.merge;

import com.intellij.openapi.vfs.VirtualFile;

public class MonitoredFileConflict {
    public VirtualFile file;
    public byte[] localData;
    public byte[] remoteData;
    public byte[] originalData;
    public boolean resolved;

    public MonitoredFileConflict(VirtualFile file) {
        this.file = file;
    }
}
