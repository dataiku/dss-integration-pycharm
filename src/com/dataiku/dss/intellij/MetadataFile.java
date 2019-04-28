package com.dataiku.dss.intellij;

import static com.google.common.base.Charsets.UTF_8;

import java.io.File;
import java.io.IOException;

import com.dataiku.dss.model.metadata.DssMetadata;
import com.dataiku.dss.model.metadata.DssRecipeMetadata;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.vfs.VirtualFile;

public class MetadataFile {
    public final VirtualFile moduleContentRoot;
    public final File recipesMetadataFile;
    public final DssMetadata metadata;

    public MetadataFile(VirtualFile moduleContentRoot, DssMetadata metadata) {
        this.moduleContentRoot = moduleContentRoot;
        this.metadata = metadata;
        this.recipesMetadataFile = new File(new File(moduleContentRoot.getCanonicalPath(), ".dss"), "recipes.json");
    }

    public void addOrUpdateRecipe(DssRecipeMetadata fileMetadata) throws IOException {
        // Update our recipe
        metadata.recipes.removeIf(recipe -> recipe.path.equals(fileMetadata.path));
        metadata.recipes.add(fileMetadata);

        // Write the file back
        flush();
    }

    public synchronized void removeRecipe(DssRecipeMetadata fileMetadata) throws IOException {
        Preconditions.checkNotNull(fileMetadata, "fileMetadata");

        // Update our recipe
        metadata.recipes.removeIf(recipe -> recipe.path.equals(fileMetadata.path));

        // Write the file back
        flush();
    }

    public void flush() throws IOException {
        writeMetadata(recipesMetadataFile, metadata);
    }

    private static void writeMetadata(File metadataFile, DssMetadata metadata) throws IOException {
        if (!metadataFile.getParentFile().exists()) {
            if (!metadataFile.getParentFile().mkdirs()) {
                throw new IOException("Unable to create directory " + metadataFile.getParentFile().getPath());
            }
        }
        Files.write(toJson(metadata).getBytes(UTF_8), metadataFile);
    }

    private static String toJson(Object recipe) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(recipe);
    }

}

