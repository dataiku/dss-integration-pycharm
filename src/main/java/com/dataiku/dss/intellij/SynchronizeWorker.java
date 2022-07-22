package com.dataiku.dss.intellij;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.actions.merge.MonitoredLibraryFileConflict;
import com.dataiku.dss.intellij.actions.merge.MonitoredPluginFileConflict;
import com.dataiku.dss.intellij.actions.merge.MonitoredRecipeFileConflict;
import com.dataiku.dss.intellij.config.DssInstance;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.intellij.utils.VirtualFileManager;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.dss.DssException;
import com.dataiku.dss.model.dss.FolderContent;
import com.dataiku.dss.model.dss.Recipe;
import com.dataiku.dss.model.dss.RecipeAndPayload;
import com.dataiku.dss.model.metadata.DssFileMetadata;
import com.dataiku.dss.model.metadata.DssFileSystemMetadata;
import com.dataiku.dss.model.metadata.DssLibraryFileMetadata;
import com.dataiku.dss.model.metadata.DssPluginFileMetadata;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.dataiku.dss.intellij.utils.VirtualFileManager.getContentHash;
import static com.google.common.base.Charsets.UTF_8;

public class SynchronizeWorker {
    private static final Logger log = Logger.getInstance(SynchronizeWorker.class);
    private static final String PYC_SUFFIX = ".pyc";
    private static final String CLASS_SUFFIX = ".class";
    private static final String DELETED_SUFFIX = ".deleted";
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
                synchronizeFileSystem(dssInstance, plugin);
            }
        }

        for (MonitoredLibrary library : request.libraries) {
            DssInstance dssInstance = settings.getDssInstance(library.library.instance);
            if (dssInstance != null) {
                synchronizeFileSystem(dssInstance, library);
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
        // De-index the missing file
        if (recipe == null || !monitoredFile.file.exists()) {
            if (recipe == null && monitoredFile.file.exists()) {
                log.info(String.format("Recipe '%s' has been remotely deleted. Stop tracking it.", monitoredFile.recipe));
                summary.conflicts.add(String.format("Recipe '%s' has been locally modified but remotely deleted. It will not be synchronized anymore.", monitoredFile.recipe));
            } else if (recipe != null && !monitoredFile.file.exists()) {
                log.info(String.format("Recipe '%s' has been locally deleted. Stop tracking it.", monitoredFile.recipe));
                summary.conflicts.add(String.format("Recipe '%s' has been locally deleted. Reopen it if you need to change it again.", monitoredFile.recipe));
            }
            monitoredFile.metadataFile.metadata.recipes.remove(monitoredFile.recipe);
            dirtyMetadataFiles.add(monitoredFile.metadataFile);
            MonitoredFilesIndex.getInstance().removeFromIndex(monitoredFile);
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
                SynchronizeUtils.saveRecipeToDss(dssClient, monitoredFile, localFileContent, false);
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
                monitoredFile.recipe.data = recipeAndPayload.payload.getBytes(UTF_8);
                monitoredFile.recipe.versionNumber = remoteVersionNumber;
                dirtyMetadataFiles.add(monitoredFile.metadataFile);
            } else {
                if (localHash == originalHash) {
                    log.info(String.format("Recipe '%s' has been remotely modified but not modified locally since last synchronization. Updating local copy of the recipe.", monitoredFile.recipe));
                    vFileManager.writeToVirtualFile(monitoredFile.file, recipeAndPayload.payload);
                    monitoredFile.recipe.contentHash = remoteHash;
                    monitoredFile.recipe.data = recipeAndPayload.payload.getBytes(UTF_8);
                    monitoredFile.recipe.versionNumber = remoteVersionNumber;
                    dirtyMetadataFiles.add(monitoredFile.metadataFile);
                    summary.locallyUpdated.add(String.format("Recipe '%s.%s' updated with latest version found on DSS instance.", monitoredFile.recipe.projectKey, monitoredFile.recipe.recipeName));
                } else {
                    // Conflict!! Save remote file as .remote and send the local version to DSS
                    log.info(String.format("Conflict detected for recipe '%s'.", monitoredFile.recipe));
                    MonitoredRecipeFileConflict conflict = new MonitoredRecipeFileConflict(monitoredFile);
                    conflict.localData = localFileContent.getBytes(UTF_8);
                    conflict.remoteData = recipeAndPayload.payload.getBytes(UTF_8);
                    conflict.originalData = monitoredFile.metadataFile.readDataBlob(monitoredFile.recipe.dataBlobId);
                    conflict.originalVersionNumber = originalVersionNumber;
                    conflict.remoteVersionNumber = remoteVersionNumber;
                    summary.fileConflicts.add(conflict);

                    summary.conflicts.add(String.format("Recipe '%s.%s' has been modified both locally and remotely.", monitoredFile.recipe.projectKey, monitoredFile.recipe.recipeName));
                }
            }
        }
    }

    private void synchronizeFileSystem(DssInstance dssInstance, MonitoredFileSystem monitoredFS) throws IOException {
        DSSClient dssClient = dssInstance.createClient();
        List<FolderContent> folderContents;
        if (monitoredFS instanceof MonitoredPlugin) {
            if(Strings.isNullOrEmpty(monitoredFS.fsMetadata.id)) {
                monitoredFS.fsMetadata.id = ((MonitoredPlugin) monitoredFS).plugin.pluginId;
            }
            folderContents = dssClient.listPluginFiles(monitoredFS.fsMetadata.id);
        }
        else {
            folderContents = dssClient.listLibraryFiles(monitoredFS.fsMetadata.id);
        }

        synchronizeFolder(dssClient, monitoredFS, monitoredFS.baseDir, folderContents);

        // Add all files in pluginBaseDir that are not in remote plugin
        Map<String, FolderContent> indexedFolderContent = index(folderContents);
        addOrDeleteMissingFiles(monitoredFS, indexedFolderContent, dssClient);
        addOrDeleteMissingFolders(monitoredFS, indexedFolderContent, dssClient);
    }

    private void addOrDeleteMissingFiles(MonitoredFileSystem monitoredFS, Map<String, FolderContent> index, DSSClient dssClient) throws IOException {
        List<VirtualFile> missingFiles = new ArrayList<>();
        String baseUrl = monitoredFS.baseDir.getUrl();
        VfsUtilCore.visitChildrenRecursively(monitoredFS.baseDir, new VirtualFileVisitor<Object>() {
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

            DssFileMetadata trackedFile = monitoredFS.findFile(path);
            if (trackedFile == null) {
                // Newly added => sent it to DSS
                log.info(String.format("Uploading locally added file '%s' (path=%s)", file.getName(), path));
                if (monitoredFS instanceof MonitoredPlugin) {
                    dssClient.uploadPluginFile(monitoredFS.fsMetadata.id, path, content);
                } else {
                    dssClient.uploadLibraryFile(monitoredFS.fsMetadata.id, path, content);
                }
                summary.dssUpdated.add(String.format("File '%s' uploaded to DSS instance.", path));
                updateFileMetadata(monitoredFS, path, contentHash, content);

            } else {
                // Has been modified locally?
                if (trackedFile.contentHash != contentHash) {
                    // Rename the file into ".deleted" and stop tracking it.
                    String newName = findNonExistingFilename(file, file.getName() + DELETED_SUFFIX);
                    vFileManager.renameVirtualFile(file, newName);
                    summary.conflicts.add(String.format("File '%s' has been removed from DSS instance but modified locally. Local copy has been renamed into '%s'.", path, newName));
                } else {
                    // No, delete the file locally
                    vFileManager.deleteVirtualFile(file);
                    summary.locallyDeleted.add(String.format("File '%s' locally deleted because it has been removed from DSS instance.", path));
                }
                removeFileMetadata(monitoredFS, path);
            }
        }
    }


    private void addOrDeleteMissingFolders(MonitoredFileSystem monitoredFS, Map<String, FolderContent> index, DSSClient dssClient) throws IOException {
        List<VirtualFile> missingFolders = new ArrayList<>();
        String baseUrl = monitoredFS.baseDir.getUrl();
        VfsUtilCore.visitChildrenRecursively(monitoredFS.baseDir, new VirtualFileVisitor<Object>() {
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

            DssFileMetadata trackedFile = monitoredFS.findFile(path);
            if (trackedFile == null) {
                // Newly added => sent it to DSS
                log.info(String.format("Uploading locally added directory '%s' (path=%s)", folder.getName(), path));
                if (monitoredFS instanceof MonitoredPlugin) {
                    dssClient.createPluginFolder(monitoredFS.fsMetadata.id, path);
                } else {
                    dssClient.createLibraryFolder(monitoredFS.fsMetadata.id, path);
                }
                summary.dssUpdated.add(String.format("Directory '%s' created into DSS instance.", path));
                updateFileMetadata(monitoredFS, path, 0, null);
            } else {
                // Is empty
                VirtualFile[] children = folder.getChildren();
                if (children == null || children.length == 0) {
                    // No, delete the file locally
                    vFileManager.deleteVirtualFile(folder);
                    summary.locallyDeleted.add(String.format("Plugin directory '%s' locally deleted because it has been removed from DSS instance.", path));
                }
                removeFileMetadata(monitoredFS, path);
            }
        }
    }


    private void synchronizeFolder(DSSClient dssClient, MonitoredFileSystem monitoredFS, VirtualFile parent, List<FolderContent> folderContents) throws IOException {
        String pluginId = monitoredFS.fsMetadata.id;
        for (FolderContent file : folderContents) {
            DssFileMetadata trackedFile = monitoredFS.findFile(file.path);
            if (file.mimeType == null || "null".equals(file.mimeType)) {
                // Folder
                log.info(String.format("Synchronize plugin folder '%s'", file.path));

                if (trackedFile == null) {
                    // Create folder
                    log.info(" - Creating folder: it has been added remotely since last synchronization.");
                    VirtualFile virtualFile = vFileManager.getOrCreateVirtualDirectory(parent, file.name);
                    updateFileMetadata(monitoredFS, file.path, 0, null);

                    if (file.children != null && !file.children.isEmpty()) {
                        synchronizeFolder(dssClient, monitoredFS, virtualFile, file.children);
                    }
                } else {
                    VirtualFile virtualFile = VirtualFileManager.getVirtualFile(parent, file.name);
                    if (virtualFile != null && virtualFile.exists() && virtualFile.isValid()) {
                        // Recurse if necessary
                        if (file.children != null && !file.children.isEmpty()) {
                            synchronizeFolder(dssClient, monitoredFS, virtualFile, file.children);
                        }
                    } else {
                        // Directory has been locally deleted since last synchronization.
                        deleteFile(dssClient, monitoredFS, pluginId, file);
                    }
                }

            } else {
                // Regular file
                log.info(String.format("Synchronize file '%s'", file.path));

                byte[] fileContent;
                if (monitoredFS instanceof MonitoredPlugin) {
                    fileContent = file.size == 0 ? new byte[0] : dssClient.downloadPluginFile(pluginId, file.path);
                } else {
                    String fileContentString = file.size == 0 ? "" : dssClient.downloadLibraryFile(pluginId, file.path).data;
                    // Converting back to bytes to factorize code with plugins
                    if (fileContentString==null || "".equals(fileContentString)) {
                        fileContent = new byte[0];
                    } else {
                        fileContent = fileContentString.getBytes(UTF_8);
                    }
                }

                if (trackedFile == null) {
                    log.info(" - Creating file: it has been added remotely since last synchronization.");
                    VirtualFile virtualFile = vFileManager.getOrCreateVirtualFile(parent, file.name);
                    vFileManager.writeToVirtualFile(virtualFile, fileContent, UTF_8);
                    updateFileMetadata(monitoredFS, file.path, getContentHash(fileContent), fileContent);
                    summary.locallyUpdated.add(String.format("File '%s' downloaded from DSS instance.", file.path));
                } else {
                    VirtualFile virtualFile = VirtualFileManager.getVirtualFile(parent, file.name);
                    if (virtualFile == null || !virtualFile.exists() || !virtualFile.isValid()) {
                        // File locally deleted.
                        deleteFile(dssClient, monitoredFS, pluginId, file);
                    } else {
                        int localHash = getContentHash(virtualFile);
                        int originalHash = trackedFile.contentHash;
                        int remoteHash = getContentHash(fileContent);
                        if (remoteHash == originalHash) {
                            // No change on remote server since last synchronization.
                            if (localHash != originalHash) {
                                // File locally modified => Upload it to DSS
                                log.info(" - Uploading file. It has been locally modified and left untouched remotely since last synchronization.");
                                byte[] content = VirtualFileManager.readVirtualFileAsByteArray(virtualFile);
                                if (monitoredFS instanceof MonitoredPlugin) {
                                    dssClient.uploadPluginFile(pluginId, file.path, content);
                                } else {
                                    dssClient.uploadLibraryFile(pluginId, file.path, content);
                                }
                                updateFileMetadata(monitoredFS, file.path, localHash, content);
                                summary.dssUpdated.add(String.format("File '%s' saved into DSS instance.", file.path));
                            } else {
                                // All files are identical, nothing to do.
                                log.info(" - Files are identical.");
                            }
                        } else {
                            // Changed on remote server since last synchronization
                            if (remoteHash == localHash) {
                                // Both files have been changed in the same way. Just update the metadata on our side.
                                log.info(" - Updated identically both locally and remotely since last synchronization.");
                                updateFileMetadata(monitoredFS, file.path, remoteHash, fileContent);
                            } else if (localHash == originalHash) {
                                // File has not been modified locally, retrieve the remote version.
                                log.info(" - Updating local file. It has been updated remotely but not locally.");
                                vFileManager.writeToVirtualFile(virtualFile, fileContent, null);
                                updateFileMetadata(monitoredFS, file.path, remoteHash, fileContent);
                                summary.locallyUpdated.add(String.format("File '%s' updated with latest version from DSS instance.", file.path));
                            } else {
                                // Conflict!! Checkout remote file as .remote and send the local version to DSS
                                log.warn(" - Conflict detected. Marking this file for future resolution.");
                                byte[] content = VirtualFileManager.readVirtualFileAsByteArray(virtualFile);
                                if (monitoredFS instanceof MonitoredPlugin) {
                                    MonitoredPluginFileConflict conflict = new MonitoredPluginFileConflict(virtualFile, (MonitoredPlugin) monitoredFS, new DssPluginFileMetadata(trackedFile.instance, trackedFile.id, trackedFile.path, trackedFile.remotePath, trackedFile.contentHash, trackedFile.dataBlobId));
                                    conflict.localData = content;
                                    conflict.remoteData = fileContent;
                                    conflict.originalData = monitoredFS.metadataFile.readDataBlob(trackedFile.dataBlobId);
                                    summary.fileConflicts.add(conflict);
                                } else {
                                    MonitoredLibraryFileConflict conflict = new MonitoredLibraryFileConflict(virtualFile, (MonitoredLibrary) monitoredFS, new DssLibraryFileMetadata(trackedFile.instance, trackedFile.id, trackedFile.path, trackedFile.remotePath, trackedFile.contentHash, trackedFile.dataBlobId));
                                    conflict.localData = content;
                                    conflict.remoteData = fileContent;
                                    conflict.originalData = monitoredFS.metadataFile.readDataBlob(trackedFile.dataBlobId);
                                    summary.fileConflicts.add(conflict);
                                }
                                summary.conflicts.add(String.format("File '%s' has been modified both locally and remotely..", file.path));
                            }
                        }
                    }
                }
            }
        }
    }


    private void deleteFile(DSSClient dssClient, MonitoredFileSystem monitoredFS, String id, FolderContent file) throws DssException {
        List<FolderContent> children = file.children;
        if (children != null) {
            for (FolderContent child : children) {
                deleteFile(dssClient, monitoredFS, id, child);
            }
        }
        log.info(String.format("Deleting file '%s' from '%s'", file.path, id));
        if (monitoredFS instanceof MonitoredPlugin) {
            dssClient.deletePluginFile(id, file.path);
        } else {
            dssClient.deleteLibraryFile(id, file.path);
        }
        monitoredFS.removeFile(file.path);
        summary.dssDeleted.add(String.format("File '%s' deleted from DSS instance.", file.path));
    }

    private void updateFileMetadata(MonitoredFileSystem monitoredFS, String path, int contentHash, byte[] content) throws IOException {
        String id = monitoredFS.fsMetadata.id;

        if (monitoredFS instanceof MonitoredPlugin) {
            DssPluginFileMetadata fileMetadata = new DssPluginFileMetadata(
                    monitoredFS.fsMetadata.instance,
                    id,
                    id + "/" + path,
                    path,
                    contentHash,
                    content);
            monitoredFS.metadataFile.addOrUpdatePluginFile(fileMetadata, false);
        } else {
            DssLibraryFileMetadata fileMetadata = new DssLibraryFileMetadata(
                    monitoredFS.fsMetadata.instance,
                    id,
                    id + "/" + path,
                    path,
                    contentHash,
                    content);
            monitoredFS.metadataFile.addOrUpdateLibraryFile(fileMetadata, false);
        }
        dirtyMetadataFiles.add(monitoredFS.metadataFile);
    }

    private void removeFileMetadata(MonitoredFileSystem monitoredFS, String path) {
        DssFileSystemMetadata fsMetadata = monitoredFS.fsMetadata;
        DssFileMetadata fileMetadata = fsMetadata.findFile(path);
        if (fileMetadata != null) {
            fsMetadata.files.remove(fileMetadata);
        }
        dirtyMetadataFiles.add(monitoredFS.metadataFile);
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

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean ignoreFile(String fileName) {
        return fileName.endsWith(DELETED_SUFFIX) || fileName.endsWith(PYC_SUFFIX) || fileName.endsWith(CLASS_SUFFIX);
    }
}
