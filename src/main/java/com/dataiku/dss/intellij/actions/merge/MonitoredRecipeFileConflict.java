package com.dataiku.dss.intellij.actions.merge;

import com.dataiku.dss.intellij.MonitoredRecipeFile;

public class MonitoredRecipeFileConflict extends MonitoredFileConflict {
    public MonitoredRecipeFile recipeFile;
    public long remoteVersionNumber;
    public long originalVersionNumber;

    public MonitoredRecipeFileConflict(MonitoredRecipeFile recipeFile) {
        super(recipeFile.file);
        this.recipeFile = recipeFile;
    }
}
