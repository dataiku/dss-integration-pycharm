package com.dataiku.dss.model.dss;

import java.util.List;

public class FolderContent {
    public String name;
    public String path;
    public String mimeType;
    public long size;
    public List<FolderContent> children;
    public String data; // the file's data. Null when listing
    public boolean hasData;
    public boolean readOnly;
    public String reason; // reason for not sending the data (if hasData = false)

    @Override
    public String toString() {
        return "FolderContent{" + "name='" + name + '\'' +
                ", path='" + path + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", size=" + size +
                ", children=" + children +
                ", data='" + data + '\'' +
                ", hasData=" + hasData +
                ", readOnly=" + readOnly +
                ", reason='" + reason + '\'' +
                '}';
    }
}
