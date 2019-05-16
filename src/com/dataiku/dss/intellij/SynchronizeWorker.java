package com.dataiku.dss.intellij;

import static com.dataiku.dss.intellij.SynchronizeUtils.saveRecipeToDss;
import static com.dataiku.dss.intellij.utils.VirtualFileManager.getContentHash;
import static com.google.common.base.Charsets.UTF_8;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.config.DssInstance;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.intellij.utils.VirtualFileManager;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.dss.DssException;
import com.dataiku.dss.model.dss.FolderContent;
import com.dataiku.dss.model.dss.Recipe;
import com.dataiku.dss.model.dss.RecipeAndPayload;
import com.dataiku.dss.model.metadata.DssPluginFileMetadata;
import com.dataiku.dss.model.metadata.DssPluginMetadata;
import com.google.common.base.Preconditions;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;

public class SynchronizeWorker {
    private static final Logger log = Logger.getInstance(SynchronizeWorker.class);
    private static final String PYC_SUFFIX = ".pyc";
    private static final String CLASS_SUFFIX = ".class";
    private static final String DELETED_SUFFIX = ".deleted";
    private static final String REMOTE_SUFFIX = ".remote";
    private final DssSettings settings;

    private final RecipeCache recipeCache;
    private final VirtualFileManager vFileManager;
    private final Set<MetadataFile> dirtyMetadataFiles = new HashSet<>();
    private final SynchronizeSummary summary = new SynchronizeSummary();

    public SynchronizeWorker(DataikuDSSPlugin dssPlugin, DssSettings settings, RecipeCache recipeCache, boolean runInBackgroundThread) {
        this.settings = settings;
        this.recipeCache = recipeCache;
        this.vFileManager = new VirtualFileManager(dssPlugin, runInBackgroundThread);
    }

    public SynchronizeSummary synchronizeWithDSS(SynchronizeRequest request) throws IOException {
        log.info("Starting synchronization at " + DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now()));
        for (MonitoredRecipeFile recipeFile : request.recipeFiles) {
            DssInstance dssInstance = settings.getDssInstance(recipeFile.recipe.instance);
            if (dssInstance != null) {
                synchronizeRecipe(dssInstance, recipeFile);
            }
        }
        for (MonitoredPlugin plugin : request.plugins) {
            DssInstance dssInstance = settings.getDssInstance(plugin.plugin.instance);
            if (dssInstance != null) {
                synchronizePlugin(dssInstance, plugin);
            }
        }

        for (MetadataFile dirtyMetadataFile : dirtyMetadataFiles) {
            dirtyMetadataFile.flush();
        }
        return summary;
    }

    private void synchronizeRecipe(DssInstance dssInstance, MonitoredRecipeFile monitoredFile) throws IOException {
        Preconditions.checkNotNull(dssInstance);
        Preconditions.checkNotNull(monitoredFile);

        DSSClient dssClient = dssInstance.createClient();
        Recipe recipe = recipeCache.getRecipe(dssInstance.id, monitoredFile.recipe.projectKey, monitoredFile.recipe.recipeName);
        if (recipe == null) {
            Messages.showErrorDialog(String.format("Recipe '%s' has been deleted from project '%s' on DSS instance.", monitoredFile.recipe.recipeName, monitoredFile.recipe.projectKey), "Synchronization Error");
            return;
        }
        long remoteVersionNumber = recipe.versionTag.versionNumber;
        int originalHash = monitoredFile.recipe.contentHash;
        long originalVersionNumber = monitoredFile.recipe.versionNumber;
        String localFileContent = VirtualFileManager.readVirtualFile(monitoredFile.file);
        int localHash = getContentHash(localFileContent);

        if (remoteVersionNumber == originalVersionNumber) {
            // No change on remote server since last synchronization.
            if (localHash != originalHash) {
                // File locally modified => Upload it to DSS
                log.info(String.format("Recipe '%s' has been locally modified. Saving it onto the remote DSS instance", monitoredFile.recipe));
                saveRecipeToDss(dssClient, monitoredFile, localFileContent, false);
                dirtyMetadataFiles.add(monitoredFile.metadataFile);
                summary.dssUpdated.add(String.format("Recipe '%s.%s' saved into DSS instance.", monitoredFile.recipe.projectKey, monitoredFile.recipe.recipeName));
            } else {
                log.info(String.format("Recipe '%s' has not been locally or remotely modified since last synchronization.", monitoredFile.recipe));
            }
        } else {
            RecipeAndPayload recipeAndPayload = dssClient.loadRecipe(monitoredFile.recipe.projectKey, monitoredFile.recipe.recipeName);
            if (recipeAndPayload == null) {
                Messages.showErrorDialog(String.format("Recipe '%s' cannot be loaded from project '%s' on DSS instance.", monitoredFile.recipe.recipeName, monitoredFile.recipe.projectKey), "Synchronization Error");
                return;
            }
            int remoteHash = getContentHash(recipeAndPayload.payload);

            // Changed on remote server since last synchronization
            if (remoteHash == localHash) {
                // Both files have been changed in the same way. Just update the metadata on our side.
                log.info(String.format("Recipe '%s' has been remotely modified but is the same as local version. Updating local metadata.", monitoredFile.recipe));
                monitoredFile.recipe.contentHash = remoteHash;
                monitoredFile.recipe.versionNumber = remoteVersionNumber;
                dirtyMetadataFiles.add(monitoredFile.metadataFile);
            } else {
                if (localHash == originalHash) {
                    log.info(String.format("Recipe '%s' has been remotely modified but not modified locally since last synchronization. Updating local copy of the recipe.", monitoredFile.recipe));
                    vFileManager.writeToVirtualFile(monitoredFile.file, recipeAndPayload.payload);
                    monitoredFile.recipe.contentHash = remoteHash;
                    monitoredFile.recipe.versionNumber = remoteVersionNumber;
                    dirtyMetadataFiles.add(monitoredFile.metadataFile);
                    summary.locallyUpdated.add(String.format("Recipe '%s.%s' updated with latest version found on DSS instance.", monitoredFile.recipe.projectKey, monitoredFile.recipe.recipeName));
                } else {
                    // Conflict!! Save remote file as .remote and send the local version to DSS
                    log.info(String.format("Conflict detected for recipe '%s' Uploading it and saving remote version locally with '%s' extension.", monitoredFile.recipe, REMOTE_SUFFIX));
                    saveRecipeToDss(dssClient, monitoredFile, localFileContent, false);
                    dirtyMetadataFiles.add(monitoredFile.metadataFile);

                    VirtualFile newFile = vFileManager.getOrCreateVirtualFile(monitoredFile.file.getParent(), monitoredFile.file.getName() + REMOTE_SUFFIX);
                    vFileManager.writeToVirtualFile(newFile, recipeAndPayload.payload.getBytes(UTF_8), UTF_8);
                    summary.conflicts.add(String.format("Recipe '%s.%s' both modified locally and remotely. Local version has been saved into DSS and remote version has been saved locally with '%s' extension.", monitoredFile.recipe.projectKey, monitoredFile.recipe.recipeName, REMOTE_SUFFIX));
                }
            }
        }
    }

    private void synchronizePlugin(DssInstance dssInstance, MonitoredPlugin monitoredPlugin) throws IOException {
        DSSClient dssClient = dssInstance.createClient();
        String pluginId = monitoredPlugin.plugin.pluginId;
        List<FolderContent> folderContents = dssClient.listPluginFiles(pluginId);

        synchronizePluginFolder(dssClient, monitoredPlugin, monitoredPlugin.pluginBaseDir, folderContents);

        // Add all files in pluginBaseDir that are not in remote plugin
        Map<String, FolderContent> indexedFolderContent = index(folderContents);
        addOrDeleteMissingFiles(monitoredPlugin, indexedFolderContent, dssClient);
        addOrDeleteMissingFolders(monitoredPlugin, indexedFolderContent, dssClient);
    }

    private void addOrDeleteMissingFiles(MonitoredPlugin monitoredPlugin, Map<String, FolderContent> index, DSSClient dssClient) throws IOException {
        List<VirtualFile> missingFiles = new ArrayList<>();
        String baseUrl = monitoredPlugin.pluginBaseDir.getUrl();
        VfsUtilCore.visitChildrenRecursively(monitoredPlugin.pluginBaseDir, new VirtualFileVisitor<Object>() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                String fileUrl = file.getUrl();
                if (fileUrl.startsWith(baseUrl) && fileUrl.length() > baseUrl.length()) {
                    String path = fileUrl.substring(baseUrl.length() + 1);
                    if (!index.containsKey(path) && !file.isDirectory() && !ignoreFile(file.getName())) {
                        missingFiles.add(file);
                    }
                }
                return super.visitFile(file);
            }
        });
        for (VirtualFile file : missingFiles) {
            String fileUrl = file.getUrl();
            String path = fileUrl.substring(baseUrl.length() + 1);
            byte[] content = VirtualFileManager.readVirtualFileAsByteArray(file);
            int contentHash = VirtualFileManager.getContentHash(content);

            DssPluginFileMetadata trackedFile = monitoredPlugin.findFile(path);
            if (trackedFile == null) {
                // Newly added => sent it to DSS
                log.info(String.format("Uploading locally added plugin file '%s' (path=%s)", file.getName(), path));
                dssClient.uploadPluginFile(monitoredPlugin.plugin.pluginId, path, content);
                summary.dssUpdated.add(String.format("Plugin file '%s' uploaded to DSS instance.", path));
                updatePluginFileMetadata(monitoredPlugin, path, contentHash);
            } else {
                // Has been modified locally?
                if (trackedFile.contentHash != contentHash) {
                    // Rename the file into ".deleted" and stop tracking it.
                    String newName = findNonExistingFilename(file, file.getName() + DELETED_SUFFIX);
                    vFileManager.renameVirtualFile(file, newName);
                    summary.conflicts.add(String.format("Plugin file '%s' removed from DSS instance but modified locally. Local copy has been renamed into '%s'.", path, newName));
                } else {
                    // No, delete the file locally
                    vFileManager.deleteVirtualFile(file);
                    summary.locallyDeleted.add(String.format("Plugin file '%s' locally deleted because it has been removed from DSS instance.", path));
                }
                removePluginFileMetadata(monitoredPlugin, path);
            }
        }
    }

    private void addOrDeleteMissingFolders(MonitoredPlugin monitoredPlugin, Map<String, FolderContent> index, DSSClient dssClient) throws IOException {
        List<VirtualFile> missingFolders = new ArrayList<>();
        String baseUrl = monitoredPlugin.pluginBaseDir.getUrl();
        VfsUtilCore.visitChildrenRecursively(monitoredPlugin.pluginBaseDir, new VirtualFileVisitor<Object>() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                String fileUrl = file.getUrl();
                if (fileUrl.startsWith(baseUrl) && fileUrl.length() > baseUrl.length()) {
                    String path = fileUrl.substring(baseUrl.length() + 1);
                    if (!index.containsKey(path) && file.isDirectory() && !ignoreFile(file.getName())) {
                        missingFolders.add(file);
                    }
                }
                return super.visitFile(file);
            }
        });
        for (VirtualFile folder : missingFolders) {
            String fileUrl = folder.getUrl();
            String path = fileUrl.substring(baseUrl.length() + 1);

            DssPluginFileMetadata trackedFile = monitoredPlugin.findFile(path);
            if (trackedFile == null) {
                // Newly added => sent it to DSS
                log.info(String.format("Uploading locally added plugin directory '%s' (path=%s)", folder.getName(), path));
                dssClient.createPluginFolder(monitoredPlugin.plugin.pluginId, path);
                summary.dssUpdated.add(String.format("Plugin directory '%s' created into DSS instance.", path));
                updatePluginFileMetadata(monitoredPlugin, path, 0);
            } else {
                // Is empty
                VirtualFile[] children = folder.getChildren();
                if (children == null || children.length == 0) {
                    // No, delete the file locally
                    vFileManager.deleteVirtualFile(folder);
                    summary.locallyDeleted.add(String.format("Plugin directory '%s' locally deleted because it has been removed from DSS instance.", path));
                }
                removePluginFileMetadata(monitoredPlugin, path);
            }
        }
    }

    private void synchronizePluginFolder(DSSClient dssClient, MonitoredPlugin monitoredPlugin, VirtualFile parent, List<FolderContent> folderContents) throws IOException {
        String pluginId = monitoredPlugin.plugin.pluginId;
        for (FolderContent pluginFile : folderContents) {
            DssPluginFileMetadata trackedFile = monitoredPlugin.findFile(pluginFile.path);
            if (pluginFile.mimeType == null || "null".equals(pluginFile.mimeType)) {
                // Folder
                log.info(String.format("Synchronize plugin folder '%s'", pluginFile.path));

                if (trackedFile == null) {
                    // Create folder
                    log.info(" - Creating folder: it has been added remotely since last synchronization.");
                    VirtualFile file = vFileManager.getOrCreateVirtualDirectory(parent, pluginFile.name);
                    updatePluginFileMetadata(monitoredPlugin, pluginFile.path, 0);

                    if (pluginFile.children != null && !pluginFile.children.isEmpty()) {
                        synchronizePluginFolder(dssClient, monitoredPlugin, file, pluginFile.children);
                    }
                } else {
                    VirtualFile file = VirtualFileManager.getVirtualFile(parent, pluginFile.name);
                    if (file != null && file.exists() && file.isValid()) {
                        // Recurse if necessary
                        if (pluginFile.children != null && !pluginFile.children.isEmpty()) {
                            synchronizePluginFolder(dssClient, monitoredPlugin, file, pluginFile.children);
                        }
                    } else {
                        // Directory has been locally deleted since last synchronization.
                        deletePluginFile(dssClient, monitoredPlugin, pluginId, pluginFile);
                    }
                }

            } else {
                // Regular file
                log.info(String.format("Synchronize plugin file '%s'", pluginFile.path));

                byte[] fileContent = pluginFile.size == 0 ? new byte[0] : dssClient.downloadPluginFile(pluginId, pluginFile.path);
                if (trackedFile == null) {
                    log.info(" - Creating file: it has been added remotely since last synchronization.");
                    VirtualFile file = vFileManager.getOrCreateVirtualFile(parent, pluginFile.name);
                    vFileManager.writeToVirtualFile(file, fileContent, UTF_8);
                    updatePluginFileMetadata(monitoredPlugin, pluginFile.path, getContentHash(fileContent));
                    summary.locallyUpdated.add(String.format("Plugin file '%s' downloaded from DSS instance.", pluginFile.path));
                } else {
                    VirtualFile file = VirtualFileManager.getVirtualFile(parent, pluginFile.name);
                    if (file == null || !file.exists() || !file.isValid()) {
                        // File locally deleted.
                        deletePluginFile(dssClient, monitoredPlugin, pluginId, pluginFile);
                    } else {
                        int localHash = getContentHash(file);
                        int originalHash = trackedFile.contentHash;
                        int remoteHash = getContentHash(fileContent);
                        if (remoteHash == originalHash) {
                            // No change on remote server since last synchronization.
                            if (localHash != originalHash) {
                                // File locally modified => Upload it to DSS
                                log.info(" - Uploading file. It has been locally modified and left untouched remotely since last synchronization.");
                                dssClient.uploadPluginFile(pluginId, pluginFile.path, VirtualFileManager.readVirtualFileAsByteArray(file));
                                updatePluginFileMetadata(monitoredPlugin, pluginFile.path, localHash);
                                summary.dssUpdated.add(String.format("Plugin file '%s' saved into DSS instance.", pluginFile.path));
                            } else {
                                // All files are identical, nothing to do.
                                log.info(" - Files are identical.");
                            }
                        } else {
                            // Changed on remote server since last synchronization
                            if (remoteHash == localHash) {
                                // Both files have been changed in the same way. Just update the metadata on our side.
                                log.info(" - Updated identically both locally and remotely since last synchronization.");
                                updatePluginFileMetadata(monitoredPlugin, pluginFile.path, remoteHash);
                            } else if (localHash == originalHash) {
                                // File has not been modified locally, retrieve the remote version.
                                log.info(" - Updating local file. It has been updated remotely but not locally.");
                                vFileManager.writeToVirtualFile(file, fileContent, UTF_8);
                                updatePluginFileMetadata(monitoredPlugin, pluginFile.path, remoteHash);
                                summary.locallyUpdated.add(String.format("Plugin file '%s' updated with latest version from DSS instance.", pluginFile.path));
                            } else {
                                // Conflict!! Checkout remote file as .remote and send the local version to DSS
                                log.warn(String.format(" - Conflict detected. Uploading it and saving remote version locally with '%s' extension.", REMOTE_SUFFIX));
                                dssClient.uploadPluginFile(pluginId, pluginFile.path, VirtualFileManager.readVirtualFileAsByteArray(file));
                                updatePluginFileMetadata(monitoredPlugin, pluginFile.path, localHash);

                                VirtualFile newFile = vFileManager.getOrCreateVirtualFile(parent, pluginFile.name + REMOTE_SUFFIX);
                                vFileManager.writeToVirtualFile(newFile, fileContent, UTF_8);
                                summary.conflicts.add(String.format("Plugin file '%s' both modified locally and remotely. Local version has been saved into DSS and remote version has been saved locally with '%s' extension.", pluginFile.path, REMOTE_SUFFIX));
                            }
                        }
                    }
                }
            }
        }
    }

    private void deletePluginFile(DSSClient dssClient, MonitoredPlugin monitoredPlugin, String pluginId, FolderContent pluginFile) throws DssException {
        List<FolderContent> children = pluginFile.children;
        if (children != null) {
            for (FolderContent child : children) {
                deletePluginFile(dssClient, monitoredPlugin, pluginId, child);
            }
        }
        log.info(String.format("Deleting plugin file '%s' from plugin '%s'", pluginFile.path, pluginId));
        dssClient.deletePluginFile(pluginId, pluginFile.path);
        monitoredPlugin.removeFile(pluginFile.path);
        summary.dssDeleted.add(String.format("Plugin file '%s' deleted from DSS instance.", pluginFile.path));
    }

    private void updatePluginFileMetadata(MonitoredPlugin monitoredPlugin, String path, int contentHash) throws IOException {
        String pluginId = monitoredPlugin.plugin.pluginId;
        DssPluginFileMetadata pluginFileMetadata = new DssPluginFileMetadata(
                monitoredPlugin.plugin.instance,
                pluginId,
                pluginId + "/" + path,
                path,
                contentHash);
        monitoredPlugin.metadataFile.addOrUpdatePluginFile(pluginFileMetadata, false);
        dirtyMetadataFiles.add(monitoredPlugin.metadataFile);
    }

    private void removePluginFileMetadata(MonitoredPlugin monitoredPlugin, String path) {
        DssPluginMetadata pluginMetadata = monitoredPlugin.plugin;
        DssPluginFileMetadata pluginFileMetadata = pluginMetadata.findFile(path);
        if (pluginFileMetadata != null) {
            pluginMetadata.files.remove(pluginFileMetadata);
        }
        dirtyMetadataFiles.add(monitoredPlugin.metadataFile);
    }

    private static Map<String, FolderContent> index(List<FolderContent> folderContents) {
        Map<String, FolderContent> folderContentIndex = new HashMap<>();
        index(folderContentIndex, folderContents);
        return folderContentIndex;
    }

    private static void index(Map<String, FolderContent> map, List<FolderContent> folderContents) {
        if (folderContents == null) {
            return;
        }
        for (FolderContent folderContent : folderContents) {
            map.put(folderContent.path, folderContent);
            index(map, folderContent.children);
        }
    }

    private static String findNonExistingFilename(VirtualFile file, String newName) {
        VirtualFile parent = file.getParent();
        if (parent.findChild(newName) == null) {
            return newName;
        }
        int index = 1;
        while (parent.findChild(newName + "(" + index + ")") != null) {
            index++;
        }
        return newName + "(" + index + ")";
    }

    private static boolean ignoreFile(String fileName) {
        return fileName.endsWith(REMOTE_SUFFIX) || fileName.endsWith(DELETED_SUFFIX) || fileName.endsWith(PYC_SUFFIX) || fileName.endsWith(CLASS_SUFFIX);
    }
}
