package com.dataiku.dss.intellij;

import java.io.File;

import com.dataiku.dss.model.metadata.DssRecipeMetadata;
import com.google.common.base.Preconditions;
import com.intellij.openapi.vfs.VirtualFile;

public class MonitoredFile {
    public final VirtualFile file;
    public final File physicalFile;
    public final MetadataFile metadataFile;
    public final DssRecipeMetadata recipe;

    public MonitoredFile(VirtualFile file, MetadataFile metadataFile, DssRecipeMetadata recipe) {
        Preconditions.checkNotNull(file, "file");
        Preconditions.checkNotNull(metadataFile, "metadataFile");
        Preconditions.checkNotNull(recipe, "recipe");
        Preconditions.checkNotNull(file.getCanonicalPath(), "recipe.canonicalPath");

        this.file = file;
        this.physicalFile = new File(file.getCanonicalPath());
        this.metadataFile = metadataFile;
        this.recipe = recipe;
    }
}
