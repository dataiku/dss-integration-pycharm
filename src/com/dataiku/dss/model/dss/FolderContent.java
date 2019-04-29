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
        final StringBuilder sb = new StringBuilder("FolderContent{");
        sb.append("name='").append(name).append('\'');
        sb.append(", path='").append(path).append('\'');
        sb.append(", mimeType='").append(mimeType).append('\'');
        sb.append(", size=").append(size);
        sb.append(", children=").append(children);
        sb.append(", data='").append(data).append('\'');
        sb.append(", hasData=").append(hasData);
        sb.append(", readOnly=").append(readOnly);
        sb.append(", reason='").append(reason).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
