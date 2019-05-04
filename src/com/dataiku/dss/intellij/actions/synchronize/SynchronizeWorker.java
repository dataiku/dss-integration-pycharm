package com.dataiku.dss.intellij.actions.synchronize;

import static com.dataiku.dss.intellij.SynchronizerUtils.saveRecipeToDss;
import static com.dataiku.dss.intellij.VirtualFileUtils.getContentHash;
import static com.dataiku.dss.intellij.VirtualFileUtils.getOrCreateVirtualDirectory;
import static com.dataiku.dss.intellij.VirtualFileUtils.getOrCreateVirtualFile;
import static com.google.common.base.Charsets.UTF_8;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.MetadataFile;
import com.dataiku.dss.intellij.MonitoredFile;
import com.dataiku.dss.intellij.MonitoredPlugin;
import com.dataiku.dss.intellij.VirtualFileUtils;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeNodeDssInstance;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeNodePlugin;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeNodePlugins;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeNodeRecipe;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeNodeRecipeProject;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeNodeRecipes;
import com.dataiku.dss.intellij.config.DssServer;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.dss.DssException;
import com.dataiku.dss.model.dss.FolderContent;
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
    private final SynchronizeModel model;
    private Object requestor = this;
    private Set<MetadataFile> dirtyMetadataFiles = new HashSet<>();
    private SynchronizeSummary summary = new SynchronizeSummary();

    public SynchronizeWorker(SynchronizeModel model) {
        Preconditions.checkNotNull(model, "model");
        Preconditions.checkNotNull(model.selectionRootNode, "model.rootNode");
        this.model = model;
    }

    public void synchronizeWithDSS() throws IOException {
        for (SynchronizeNodeDssInstance instanceNode : model.selectionRootNode.getInstanceNodes()) {
            SynchronizeNodeRecipes recipesNode = instanceNode.getRecipesNode();
            if (recipesNode != null) {
                for (SynchronizeNodeRecipeProject projectNode : recipesNode.getProjectNodes()) {
                    for (SynchronizeNodeRecipe recipeNode : projectNode.getRecipeNodes()) {
                        if (recipeNode.isSelected()) {
                            synchronizeRecipe(instanceNode.dssServer, recipeNode.recipe);
                        }
                    }
                }
            }

            SynchronizeNodePlugins pluginsNode = instanceNode.getPluginsNode();
            if (pluginsNode != null) {
                for (SynchronizeNodePlugin pluginNode : pluginsNode.getPluginNodes()) {
                    if (pluginNode.isSelected()) {
                        synchronizePlugin(instanceNode.dssServer, pluginNode.monitoredPlugin);
                    }
                }
            }
        }

        for (MetadataFile dirtyMetadataFile : dirtyMetadataFiles) {
            dirtyMetadataFile.flush();
        }
        model.summary = summary;
    }

    private void synchronizeRecipe(DssServer dssServer, MonitoredFile monitoredFile) throws IOException {
        DSSClient dssClient = dssServer.createClient();
        RecipeAndPayload recipeAndPayload = dssClient.loadRecipe(monitoredFile.recipe.projectKey, monitoredFile.recipe.recipeName);
        if (recipeAndPayload == null) {
            Messages.showErrorDialog(String.format("Recipe '%s' has been deleted from project '%s' on DSS instance.", monitoredFile.recipe.recipeName, monitoredFile.recipe.projectKey), "Synchronization Error");
            return;
        }
        int remoteHash = getContentHash(recipeAndPayload.payload);
        long remoteVersionNumber = recipeAndPayload.recipe.versionTag.versionNumber;
        int originalHash = monitoredFile.recipe.contentHash;
        long originalVersionNumber = monitoredFile.recipe.versionNumber;
        String localFileContent = VirtualFileUtils.readVirtualFile(monitoredFile.file);
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
                log.info(String.format("Recipe '%s' has not been locally or remote modified since last synchronization.", monitoredFile.recipe));
            }
        } else {
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
                    VirtualFileUtils.writeToVirtualFile(monitoredFile.file, recipeAndPayload.payload);
                    monitoredFile.recipe.contentHash = remoteHash;
                    monitoredFile.recipe.versionNumber = remoteVersionNumber;
                    dirtyMetadataFiles.add(monitoredFile.metadataFile);
                    summary.locallyUpdated.add(String.format("Recipe '%s.%s' updated with latest version found on DSS instance.", monitoredFile.recipe.projectKey, monitoredFile.recipe.recipeName));
                } else {
                    // Conflict!! Save remote file as .remote and send the local version to DSS
                    log.info(String.format("Conflict detected for recipe '%s' Uploading it and saving remote version locally with '.remote' extension.", monitoredFile.recipe));
                    saveRecipeToDss(dssClient, monitoredFile, localFileContent, false);
                    dirtyMetadataFiles.add(monitoredFile.metadataFile);

                    VirtualFile newFile = VirtualFileUtils.getOrCreateVirtualFile(requestor, monitoredFile.file.getParent(), monitoredFile.file.getName() + ".remote");
                    VirtualFileUtils.writeToVirtualFile(newFile, recipeAndPayload.payload.getBytes(UTF_8), UTF_8);
                    summary.conflicts.add(String.format("Recipe '%s.%s' both modified locally and remotely. Local version has been saved into DSS and remote version has been saved locally with '.remote' extension.", monitoredFile.recipe.projectKey, monitoredFile.recipe.recipeName));
                }
            }
        }
    }


    private void synchronizePlugin(DssServer dssInstance, MonitoredPlugin monitoredPlugin) throws IOException {
        DSSClient dssClient = dssInstance.createClient();
        String pluginId = monitoredPlugin.plugin.pluginId;
        List<FolderContent> folderContents = dssClient.listPluginFiles(pluginId);

        synchronizePluginFolder(dssClient, monitoredPlugin, monitoredPlugin.pluginBaseDir, folderContents);

        // Add all files in pluginBaseDir that are not in remote plugin
        addMissingFiles(monitoredPlugin, index(folderContents), dssClient);
    }

    private void addMissingFiles(MonitoredPlugin monitoredPlugin, Map<String, FolderContent> index, DSSClient dssClient) throws IOException {
        List<VirtualFile> missingFiles = new ArrayList<>();
        String baseUrl = monitoredPlugin.pluginBaseDir.getUrl();
        VfsUtilCore.visitChildrenRecursively(monitoredPlugin.pluginBaseDir, new VirtualFileVisitor<Object>() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
                String fileUrl = file.getUrl();
                if (fileUrl.startsWith(baseUrl) && fileUrl.length() > baseUrl.length()) {
                    String path = fileUrl.substring(baseUrl.length() + 1);
                    if (!index.containsKey(path) && !file.isDirectory() && !file.getName().endsWith(".remote")) {
                        missingFiles.add(file);
                    }
                }
                return super.visitFile(file);
            }
        });
        for (VirtualFile file : missingFiles) {
            String fileUrl = file.getUrl();
            String path = fileUrl.substring(baseUrl.length());
            byte[] content = VirtualFileUtils.readVirtualFileAsByteArray(file);
            int contentHash = VirtualFileUtils.getContentHash(content);

            DssPluginFileMetadata trackedFile = findFile(monitoredPlugin.plugin.files, path);
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
                    String newName = findNonExistingFilename(file, file.getName() + ".deleted");
                    VirtualFileUtils.renameVirtualFile(requestor, file, newName);
                    summary.conflicts.add(String.format("Plugin file '%s' removed from DSS instance but modified locally. Local copy has been renamed into '%s'.", path, newName));
                } else {
                    // No, delete the file locally
                    VirtualFileUtils.deleteVirtualFile(requestor, file);
                    summary.locallyDeleted.add(String.format("Plugin file '%s' locally deleted because it has been removed from DSS instance.", path));
                }
                removePluginFileMetadata(monitoredPlugin, path);
            }
        }
    }

    private String findNonExistingFilename(VirtualFile file, String newName) {
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

    @NotNull
    private Map<String, FolderContent> index(List<FolderContent> folderContents) {
        Map<String, FolderContent> folderContentIndex = new HashMap<>();
        index(folderContentIndex, folderContents);
        return folderContentIndex;
    }

    private void index(Map<String, FolderContent> map, List<FolderContent> folderContents) {
        if (folderContents == null) {
            return;
        }
        for (FolderContent folderContent : folderContents) {
            map.put(folderContent.path, folderContent);
            index(map, folderContent.children);
        }
    }

    private void synchronizePluginFolder(DSSClient dssClient, MonitoredPlugin monitoredPlugin, VirtualFile parent, List<FolderContent> folderContents) throws IOException {
        String pluginId = monitoredPlugin.plugin.pluginId;
        for (FolderContent pluginFile : folderContents) {
            DssPluginFileMetadata trackedFile = findFile(monitoredPlugin.plugin.files, pluginFile.path);
            if (pluginFile.mimeType == null || "null".equals(pluginFile.mimeType)) {
                // Folder
                log.info(String.format("Synchronize plugin folder '%s'", pluginFile.path));

                if (trackedFile == null) {
                    // Create folder
                    log.info(" - Creating folder: it has been added remotely since last synchronization.");
                    VirtualFile file = getOrCreateVirtualDirectory(requestor, parent, pluginFile.name);
                    updatePluginFileMetadata(monitoredPlugin, pluginFile.path, 0);

                    if (pluginFile.children != null && !pluginFile.children.isEmpty()) {
                        synchronizePluginFolder(dssClient, monitoredPlugin, file, pluginFile.children);
                    }
                } else {
                    VirtualFile file = VirtualFileUtils.getVirtualFile(parent, pluginFile.name);
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
                    VirtualFile file = VirtualFileUtils.getOrCreateVirtualFile(requestor, parent, pluginFile.name);
                    VirtualFileUtils.writeToVirtualFile(file, fileContent, UTF_8);
                    updatePluginFileMetadata(monitoredPlugin, pluginFile.path, getContentHash(fileContent));
                    summary.locallyUpdated.add(String.format("Plugin file '%s' downloaded from DSS instance.", pluginFile.path));
                } else {
                    VirtualFile file = VirtualFileUtils.getVirtualFile(parent, pluginFile.name);
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
                                dssClient.uploadPluginFile(pluginId, pluginFile.path, VirtualFileUtils.readVirtualFileAsByteArray(file));
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
                                trackedFile.contentHash = remoteHash;
                                updatePluginFileMetadata(monitoredPlugin, pluginFile.path, remoteHash);
                            } else if (localHash == originalHash) {
                                // File has not been modified locally, retrieve the remote version.
                                log.warn(" - Updating local file: it has been updated remotely but not locally.");
                                VirtualFileUtils.writeToVirtualFile(file, fileContent, UTF_8);
                                updatePluginFileMetadata(monitoredPlugin, pluginFile.path, remoteHash);
                                summary.locallyUpdated.add(String.format("Plugin file '%s' updated with latest version from DSS instance.", pluginFile.path));
                            } else {
                                // Conflict!! Checkout remote file as .remote and send the local version to DSS
                                log.warn(" - Conflict detected. Uploading it and saving remote version locally with '.remote' extension.");
                                dssClient.uploadPluginFile(pluginId, pluginFile.path, VirtualFileUtils.readVirtualFileAsByteArray(file));
                                updatePluginFileMetadata(monitoredPlugin, pluginFile.path, localHash);

                                VirtualFile newFile = getOrCreateVirtualFile(requestor, parent, pluginFile.name + ".remote");
                                VirtualFileUtils.writeToVirtualFile(newFile, fileContent, UTF_8);
                                summary.conflicts.add(String.format("Plugin file '%s' both modified locally and remotely. Local version has been saved into DSS and remote version has been saved locally with '.remote' extension.", pluginFile.path));
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
        removeFile(monitoredPlugin.plugin.files, pluginFile.path);
        summary.dssDeleted.add(String.format("Plugin file '%s' deleted from DSS instance.", pluginFile.path));
    }

    private void updatePluginFileMetadata(MonitoredPlugin monitoredPlugin, String path, int contentHash) throws IOException {
        DssPluginMetadata pluginMetadata = monitoredPlugin.plugin;
        String pluginId = monitoredPlugin.plugin.pluginId;
        DssPluginFileMetadata pluginFileMetadata = findFile(pluginMetadata.files, path);
        if (pluginFileMetadata == null) {
            pluginFileMetadata = new DssPluginFileMetadata();
            pluginMetadata.files.add(pluginFileMetadata);
        }
        // Write metadata
        pluginFileMetadata.pluginId = pluginId;
        pluginFileMetadata.instance = pluginMetadata.instance;
        pluginFileMetadata.path = pluginId + "/" + path;
        pluginFileMetadata.remotePath = path;
        pluginFileMetadata.contentHash = contentHash;
        dirtyMetadataFiles.add(monitoredPlugin.metadataFile);
    }

    private void removePluginFileMetadata(MonitoredPlugin monitoredPlugin, String path) throws IOException {
        DssPluginMetadata pluginMetadata = monitoredPlugin.plugin;
        DssPluginFileMetadata pluginFileMetadata = findFile(pluginMetadata.files, path);
        if (pluginFileMetadata != null) {
            pluginMetadata.files.remove(pluginFileMetadata);
        }
        dirtyMetadataFiles.add(monitoredPlugin.metadataFile);
    }

    private DssPluginFileMetadata findFile(List<DssPluginFileMetadata> files, String path) {
        for (DssPluginFileMetadata file : files) {
            if (path.equals(file.remotePath)) {
                return file;
            }
        }
        return null;
    }

    private void removeFile(List<DssPluginFileMetadata> files, String path) {
        for (Iterator<DssPluginFileMetadata> iterator = files.iterator(); iterator.hasNext(); ) {
            if (path.equals(iterator.next().remotePath)) {
                iterator.remove();
                return;
            }
        }
    }
}
