package com.dataiku.dss.intellij;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.utils.ComponentUtils;
import com.dataiku.dss.intellij.utils.VirtualFileUtils;
import com.dataiku.dss.model.metadata.DssMetadata;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.vfs.VirtualFile;

public class MetadataFilesIndex implements ApplicationComponent {
    private static final Logger log = Logger.getInstance(MetadataFilesIndex.class);

    public static MetadataFilesIndex getInstance() {
        return ComponentUtils.getComponent(MetadataFilesIndex.class);
    }

    @NotNull
    @Override
    public String getComponentName() {
        return "DSSMetadataFilesIndex";
    }

    public final Map<String/*Root path of module*/, MetadataFile> metadataFiles = new HashMap<>();

    @Override
    public void initComponent() {
    }

    @Override
    public void disposeComponent() {
        metadataFiles.clear();
    }

    public synchronized MetadataFile getMetadata(VirtualFile moduleContentRoot) {
        return getMetadata(moduleContentRoot, false);
    }

    public synchronized MetadataFile getOrCreateMetadata(VirtualFile moduleContentRoot) {
        return getMetadata(moduleContentRoot, true);
    }

    private synchronized MetadataFile getMetadata(VirtualFile moduleContentRoot, boolean createIfNeeded) {
        String key = moduleContentRoot.getCanonicalPath();
        MetadataFile dssMetadataFile = metadataFiles.get(key);
        if (dssMetadataFile == null) {
            try {
                DssMetadata dssMetadata = readMetadata(moduleContentRoot);
                if (dssMetadata != null) {
                    dssMetadataFile = new MetadataFile(moduleContentRoot, dssMetadata);
                    metadataFiles.put(key, dssMetadataFile);
                } else if (createIfNeeded) {
                    // Create metadata file
                    dssMetadata = new DssMetadata();
                    dssMetadata.version = 1;
                    dssMetadata.recipes = new ArrayList<>();
                    dssMetadata.plugins = new ArrayList<>();
                    dssMetadataFile = new MetadataFile(moduleContentRoot, dssMetadata);
                    dssMetadataFile.flush();
                }
            } catch (IOException e) {
                log.warn(String.format("Unable to read DSS metadata file for module '%s'", moduleContentRoot));
            }
        }
        return dssMetadataFile;
    }

    private static DssMetadata readMetadata(VirtualFile moduleRootFolder) throws IOException {
        VirtualFile metadataFile = getMetadataFile(moduleRootFolder);
        if (metadataFile == null || !metadataFile.isValid() || !metadataFile.exists()) {
            return null;
        }
        DssMetadata result = new GsonBuilder().create().fromJson(VirtualFileUtils.readVirtualFile(metadataFile), DssMetadata.class);
        if (result.plugins == null) {
            result.plugins = new ArrayList<>();
        }
        if (result.recipes == null) {
            result.recipes = new ArrayList<>();
        }
        return result;
    }

    private static VirtualFile getMetadataFile(VirtualFile moduleRootFolder) {
        return moduleRootFolder.findFileByRelativePath(".dataiku/metadata.json");
    }
}
