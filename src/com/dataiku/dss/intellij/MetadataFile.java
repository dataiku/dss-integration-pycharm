package com.dataiku.dss.intellij;

import static com.google.common.base.Charsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import com.dataiku.dss.model.metadata.DssMetadata;
import com.dataiku.dss.model.metadata.DssPluginFileMetadata;
import com.dataiku.dss.model.metadata.DssPluginMetadata;
import com.dataiku.dss.model.metadata.DssRecipeMetadata;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.vfs.VirtualFile;

public class MetadataFile {
    private final File metadataFile;
    public final DssMetadata metadata;

    MetadataFile(VirtualFile moduleContentRoot, DssMetadata metadata) {
        this.metadata = metadata;
        this.metadataFile = new File(new File(moduleContentRoot.getCanonicalPath(), ".dataiku"), "metadata.json");
    }

    public synchronized void addOrUpdateRecipe(DssRecipeMetadata fileMetadata) throws IOException {
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

    public synchronized void addOrUpdatePlugin(DssPluginMetadata plugin) throws IOException {
        Preconditions.checkNotNull(plugin, "plugin");
        Preconditions.checkNotNull(plugin.path, "plugin.path");
        Preconditions.checkNotNull(plugin.instance, "plugin.instance");

        // Update our recipe
        metadata.plugins.removeIf(p -> plugin.pluginId.equals(p.pluginId));
        metadata.plugins.add(plugin);

        // Write the file back
        flush();
    }

    public synchronized void removePlugin(String pluginId) throws IOException {
        Preconditions.checkNotNull(pluginId, "pluginId");

        DssPluginMetadata pluginMetadata = metadata.getPluginById(pluginId);
        if (pluginMetadata != null) {
            metadata.plugins.remove(pluginMetadata);

            // Write the file back
            flush();
        }
    }

    public synchronized void addOrUpdatePluginFile(DssPluginFileMetadata fileMetadata, boolean flush) throws IOException {
        Preconditions.checkNotNull(fileMetadata, "fileMetadata");

        // Update our recipe
        DssPluginMetadata pluginMetadata = metadata.getPluginById(fileMetadata.pluginId);
        if (pluginMetadata == null) {
            throw new IllegalArgumentException("Untracked plugin: " + fileMetadata.pluginId);
        }

        pluginMetadata.files.removeIf(f -> f.path.equals(fileMetadata.path));
        pluginMetadata.files.add(fileMetadata);

        // Write the file back
        if (flush) {
            flush();
        }
    }

    public synchronized void removePluginFile(DssPluginFileMetadata fileMetadata, boolean flush) throws IOException {
        Preconditions.checkNotNull(fileMetadata, "fileMetadata");

        DssPluginMetadata pluginMetadata = metadata.getPluginById(fileMetadata.pluginId);
        if (pluginMetadata != null) {
            pluginMetadata.files.removeIf(f -> f.path.equals(fileMetadata.path));

            // Write the file back
            if (flush) {
                flush();
            }
        }
    }

    public void flush() throws IOException {
        writeMetadata(metadataFile, metadata);
    }

    private static void writeMetadata(File metadataFile, DssMetadata metadata) throws IOException {
        if (!metadataFile.getParentFile().exists()) {
            if (!metadataFile.getParentFile().mkdirs()) {
                throw new IOException("Unable to create directory " + metadataFile.getParentFile().getPath());
            }
        }
        Files.write(toJson(metadata).getBytes(UTF_8), metadataFile);
    }

    private static String toJson(Object obj) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(obj);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MetadataFile that = (MetadataFile) o;
        return Objects.equals(metadataFile, that.metadataFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metadataFile);
    }
}

