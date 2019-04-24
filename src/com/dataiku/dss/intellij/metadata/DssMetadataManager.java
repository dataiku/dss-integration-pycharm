package com.dataiku.dss.intellij.metadata;

import static com.dataiku.dss.intellij.VirtualFileUtils.createFile;
import static com.dataiku.dss.intellij.VirtualFileUtils.getOrCreateDirectory;
import static com.dataiku.dss.intellij.VirtualFileUtils.writeToFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import com.dataiku.dss.intellij.VirtualFileUtils;
import com.google.common.base.Charsets;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.vfs.VirtualFile;

public class DssMetadataManager {

    public static void writeMetadata(VirtualFile metadataFile, DssMetadata metadata) throws IOException {
        writeToFile(metadataFile, toJson(metadata));
    }

    public static void writeMetadata(File metadataFile, DssMetadata metadata) throws IOException {
        com.google.common.io.Files.write(toJson(metadata).getBytes(Charsets.UTF_8), metadataFile);
    }

    public static void updateRecipeMetadata(DssMetadata metadata, VirtualFile metadataFile, DssRecipeMetadata fileMetadata) throws IOException {
        // Update our recipe
        metadata.recipes.removeIf(recipe -> recipe.path.equals(fileMetadata.path));
        metadata.recipes.add(fileMetadata);

        // Write the file back
        writeMetadata(metadataFile, metadata);
    }

    public static void updateRecipeMetadata(Object requestor, VirtualFile moduleRootFolder, DssRecipeMetadata fileMetadata) throws IOException {
        VirtualFile metadataDir = getOrCreateDirectory(requestor, moduleRootFolder, ".dss");
        VirtualFile metadataFile = metadataDir.findChild("recipes.json");

        // Read the existing metadata
        DssMetadata metadata;
        if (metadataFile != null && metadataFile.exists()) {
            metadata = new GsonBuilder().create().fromJson(VirtualFileUtils.readFile(metadataFile), DssMetadata.class);
        } else {
            metadata = new DssMetadata();
            metadata.version = 1;
            metadata.recipes = new ArrayList<>();
            metadataFile = createFile(requestor, metadataDir, "recipes.json");
        }

        // Update our recipe
        metadata.recipes.removeIf(recipe -> recipe.path.equals(fileMetadata.path));
        metadata.recipes.add(fileMetadata);

        // Write the file back
        writeMetadata(metadataFile, metadata);
    }

    public static DssMetadata readMetadata(VirtualFile moduleRootFolder) throws IOException {
        if (moduleRootFolder == null) {
            return null;
        }
        VirtualFile metadataDir = moduleRootFolder.findChild(".dss");
        if (metadataDir == null) {
            return null;
        }
        VirtualFile metadataFile = metadataDir.findChild("recipes.json");
        if (metadataFile == null || !metadataFile.exists()) {
            return null;
        }

        // Read the existing metadata
        return new GsonBuilder().create().fromJson(VirtualFileUtils.readFile(metadataFile), DssMetadata.class);
    }

    private static String toJson(Object recipe) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(recipe);
    }
}
