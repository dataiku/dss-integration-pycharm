package com.dataiku.dss.intellij;

import com.dataiku.dss.Logger;
import com.dataiku.dss.model.metadata.*;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static com.google.common.base.Charsets.UTF_8;

public class MetadataFile {
    private static final Logger log = Logger.getInstance(MetadataFile.class);
    private static final Pattern BLOB_NAME_PATTERN = Pattern.compile("^[a-f0-9]{32}$");
    private static final String BLOBS_DIRECTORY = "blobs";

    public final File metadataFile;
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

    public synchronized void addOrUpdateLibrary(DssLibraryMetadata library) throws IOException {
        Preconditions.checkNotNull(library, "library");
        Preconditions.checkNotNull(library.path, "library.path");
        Preconditions.checkNotNull(library.instance, "library.instance");

        // Update our recipes
        metadata.libraries.removeIf(p -> library.projectKey.equals(p.projectKey));
        metadata.libraries.add(library);

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

    public synchronized void removeLibrary(String projectKey) throws IOException {
        Preconditions.checkNotNull(projectKey, "projectKey");

        DssLibraryMetadata libMetadata = metadata.getLibByProjectKey(projectKey);
        if (libMetadata != null) {
            metadata.libraries.remove(libMetadata);

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

    public synchronized void addOrUpdateLibraryFile(DssLibraryFileMetadata fileMetadata, boolean flush) throws IOException {
        Preconditions.checkNotNull(fileMetadata, "fileMetadata");

        // Update our recipe
        DssLibraryMetadata libMetadata = metadata.getLibByProjectKey(fileMetadata.projectKey);
        if (libMetadata == null) {
            throw new IllegalArgumentException("Untracked project library: " + fileMetadata.projectKey);
        }

        libMetadata.files.removeIf(f -> f.path.equals(fileMetadata.path));
        libMetadata.files.add(fileMetadata);

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
        writeMetadata();
    }

    private void writeMetadata() throws IOException {
        File parentFile = metadataFile.getParentFile();
        if (!parentFile.exists()) {
            if (!parentFile.mkdirs()) {
                throw new IOException("Unable to create directory " + parentFile.getPath());
            }
        }

        // Save all new data-blobs
        flushBlobs();

        // Save the main file
        Files.write(toJson(metadata).getBytes(UTF_8), metadataFile);

        // Delete all unused blob files
        cleanUpBlobs(listBlobIds());
    }

    private void flushBlobs() throws IOException {
        for (DssRecipeMetadata recipe : metadata.recipes) {
            if (recipe.data != null) {
                recipe.dataBlobId = writeDataBlob(recipe.data);
                recipe.data = null;
            }
        }
        for (DssPluginMetadata plugin : metadata.plugins) {
            List<DssFileMetadata> files = plugin.files;
            for (DssFileMetadata file : files) {
                if (file.data != null) {
                    file.dataBlobId = writeDataBlob(file.data);
                    file.data = null;
                }
            }
        }
        for (DssLibraryMetadata library : metadata.libraries) {
            List<DssFileMetadata> files = library.files;
            for (DssFileMetadata file : files) {
                if (file.data != null) {
                    file.dataBlobId = writeDataBlob(file.data);
                    file.data = null;
                }
            }
        }

    }

    @NotNull
    private Set<String> listBlobIds() {
        Set<String> referencedBlobIds = new HashSet<>();
        for (DssRecipeMetadata recipe : metadata.recipes) {
            if (recipe.dataBlobId != null) {
                referencedBlobIds.add(recipe.dataBlobId);
            }
        }
        for (DssPluginMetadata plugin : metadata.plugins) {
            List<DssFileMetadata> files = plugin.files;
            for (DssFileMetadata file : files) {
                if (file.dataBlobId != null) {
                    referencedBlobIds.add(file.dataBlobId);
                }
            }
        }

        for (DssLibraryMetadata library : metadata.libraries) {
            List<DssFileMetadata> files = library.files;
            for (DssFileMetadata file : files) {
                if (file.dataBlobId != null) {
                    referencedBlobIds.add(file.dataBlobId);
                }
            }
        }

        return referencedBlobIds;
    }

    private void cleanUpBlobs(Set<String> referencedBlobIds) {
        File blobsDir = new File(metadataFile.getParentFile(), BLOBS_DIRECTORY);
        File[] files = blobsDir.listFiles((dir, name) -> BLOB_NAME_PATTERN.matcher(name).matches() && !referencedBlobIds.contains(name));
        if (files != null) {
            for (File file : files) {
                if (!file.delete()) {
                    log.info("Unable to delete unused blob file: " + file);
                }
            }
        }
    }

    public String writeDataBlob(byte[] data) throws IOException {
        File blobsDir = new File(metadataFile.getParentFile(), BLOBS_DIRECTORY);
        if (!blobsDir.exists()) {
            if (!blobsDir.mkdirs()) {
                throw new IOException("Unable to create directory " + blobsDir.getPath());
            }
        }
        String blobId = UUID.randomUUID().toString().replaceAll("-", "");
        File file = new File(blobsDir, blobId);
        try (OutputStream out = new GZIPOutputStream(new FileOutputStream(file))) {
            out.write(data);
            out.flush();
        }
        return blobId;
    }

    public byte[] readDataBlob(String blobId) throws IOException {
        File blobIdFile = new File(new File(metadataFile.getParentFile(), BLOBS_DIRECTORY), blobId);
        if (blobIdFile.exists()) {
            try (InputStream in = new GZIPInputStream(new FileInputStream(blobIdFile))) {
                return ByteStreams.toByteArray(in);
            }
        }
        return null;
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

