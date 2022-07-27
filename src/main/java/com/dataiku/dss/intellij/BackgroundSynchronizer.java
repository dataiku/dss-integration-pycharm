package com.dataiku.dss.intellij;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.config.DssInstance;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.intellij.utils.VirtualFileManager;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.dss.RecipeAndPayload;
import com.dataiku.dss.model.metadata.DssFileMetadata;
import com.google.common.base.Strings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.VetoableProjectManagerListener;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static com.dataiku.dss.intellij.SynchronizeUtils.*;
import static com.dataiku.dss.intellij.utils.VirtualFileManager.getContentHash;
import static com.google.common.base.Charsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

public class BackgroundSynchronizer implements ApplicationComponent {
    private static final Logger log = Logger.getInstance(BackgroundSynchronizer.class);
    private static final int INITIAL_DELAY = 10; // Wait for 10 seconds before triggering the first synchronization
    private static final int NOW = 0;

    private final MonitoredFilesIndex monitoredFilesIndex;
    private final SynchronizationNotifier synchronizationNotifier;
    private final DataikuDSSPlugin dssPlugin;
    private final DssSettings dssSettings;

    private SyncProjectManagerAdapter projectManagerAdapter;
    private ScheduledExecutorService executorService;
    private int currentPollingInterval = -1; // Negative if not scheduled
    private ScheduledFuture<?> scheduledFuture = null; // null if not scheduled
    private VirtualFileAdapter virtualFileAdapter;
    private DssSettingsListener dssSettingsListener;

    public BackgroundSynchronizer(DataikuDSSPlugin dssPlugin, DssSettings dssSettings, MonitoredFilesIndex monitoredFilesIndex, SynchronizationNotifier synchronizationNotifier) {
        this.dssPlugin = dssPlugin;
        this.dssSettings = dssSettings;
        this.monitoredFilesIndex = monitoredFilesIndex;
        this.synchronizationNotifier = synchronizationNotifier;
    }

    @NotNull
    public String getComponentName() {
        return "DSSPluginBackgroundSynchronizer";
    }

    @Override
    public void initComponent() {
        log.info("Starting");
        executorService = Executors.newSingleThreadScheduledExecutor();

        // At startup, synchronize everything, then poll DSS every X seconds if one (or more) monitored recipes has been updated on DSS side.
        if (dssSettings.isBackgroundSynchronizationEnabled()) {
            scheduleSynchronization(INITIAL_DELAY);
        }
        // When the configuration is changed, update the polling interval or cancel the synchronization scheduling
        dssSettingsListener = new DssSettingsListener();
        dssSettings.addListener(dssSettingsListener);


        // Whenever a new project is opened, we rescan the .dataiku directories to find new items to monitor
        projectManagerAdapter = new SyncProjectManagerAdapter();
        ProjectManager.getInstance().addProjectManagerListener(projectManagerAdapter);

        // Whenever a monitored file is saved in IntelliJ, upload it onto DSS.
        virtualFileAdapter = new VirtualFileAdapter();
        LocalFileSystem.getInstance().addVirtualFileListener(virtualFileAdapter);
    }

    private void scheduleSynchronization(long initialDelay) {
        cancelSynchronization();

        // Create a new scheduling
        currentPollingInterval = Math.max(10, dssSettings.getBackgroundSynchronizationPollIntervalInSeconds());
        log.info(String.format("Scheduling background synchronization (polling every %d seconds after initial delay of %d seconds)", currentPollingInterval, initialDelay));
        scheduledFuture = executorService.scheduleWithFixedDelay(this::runSynchronizer, initialDelay, currentPollingInterval, SECONDS);
    }

    @Override
    public void disposeComponent() {
        log.info("Stopping");

        dssSettings.removeListener(dssSettingsListener);

        LocalFileSystem.getInstance().removeVirtualFileListener(virtualFileAdapter);

        ProjectManager.getInstance().removeProjectManagerListener(projectManagerAdapter);
        projectManagerAdapter = null;

        cancelSynchronization();
    }

    private void cancelSynchronization() {
        if (scheduledFuture != null) {
            log.info(String.format("Cancelling background synchronization (was polling every %d seconds)", currentPollingInterval));
            scheduledFuture.cancel(false);
            scheduledFuture = null;
            currentPollingInterval = -1;
        }
    }

    private void runSynchronizer() {
        try {
            log.debug("RunSynchronizer...");
            SynchronizeRequest request = buildRequest(monitoredFilesIndex);
            if (!request.isEmpty()) {
                try {
                    SynchronizeSummary summary = new SynchronizeWorker(dssPlugin, dssSettings, new RecipeCache(dssSettings), true).synchronizeWithDSS(request);
                    if (!summary.isEmpty()) {
                        synchronizationNotifier.notifySuccess(summary, null);
                    }
                } catch (IOException e) {
                    synchronizationNotifier.notifyFailure(e, null);
                }
            }
        } catch (Exception e) {
            log.error("Caught exception while running synchronizer", e);
        }
    }

    private SynchronizeRequest buildRequest(MonitoredFilesIndex monitoredFilesIndex) {
        return new SynchronizeRequest(monitoredFilesIndex.getMonitoredRecipeFiles(),
                monitoredFilesIndex.getMonitoredPlugins(), monitoredFilesIndex.getMonitoredLibraries());
    }

    private class SyncProjectManagerAdapter implements VetoableProjectManagerListener {
        @Override
        public void projectOpened(Project project) {
            // Index all files present in newly opened project
            monitoredFilesIndex.index(new Project[]{project});
            if (dssSettings.isBackgroundSynchronizationEnabled()) {
                scheduleSynchronization(NOW);
            }
        }

        @Override
        public void projectClosingBeforeSave(@NotNull Project project) {
            runSynchronizer();
        }

        @Override
        public boolean canClose(@NotNull Project project) {
            return true;
        }
    }

    private class VirtualFileAdapter implements VirtualFileListener {
        @Override
        public void fileCreated(@NotNull VirtualFileEvent event) {
            if (!dssSettings.isBackgroundSynchronizationEnabled()) {
                return;
            }
            if (monitoredFilesIndex.getMonitoredPlugin(event.getFile()) != null) {
                scheduleSynchronization(NOW);
            }
        }

        @Override
        public void fileCopied(@NotNull VirtualFileCopyEvent event) {
            if (!dssSettings.isBackgroundSynchronizationEnabled()) {
                return;
            }
            if (monitoredFilesIndex.getMonitoredPlugin(event.getOriginalFile()) != null
                    || monitoredFilesIndex.getMonitoredPlugin(event.getFile()) != null) {
                scheduleSynchronization(NOW);
            }
            if (monitoredFilesIndex.getMonitoredLibrary(event.getOriginalFile()) != null
                    || monitoredFilesIndex.getMonitoredLibrary(event.getFile()) != null) {
                scheduleSynchronization(NOW);
            }
        }

        @Override
        public void fileDeleted(@NotNull VirtualFileEvent event) {
            // We want to de-index files
            VirtualFile file = event.getFile();
            if (file.isDirectory()) {
                MonitoredFileSystem deletedFileSystem = monitoredFilesIndex.getMonitoredFileSystemFromBaseDir(file);
                if (deletedFileSystem != null) {
                    monitoredFilesIndex.removeFromIndex(deletedFileSystem);
                    try {
                        if (deletedFileSystem instanceof MonitoredPlugin) {
                            deletedFileSystem.metadataFile.removePlugin(deletedFileSystem.fsMetadata.id);
                        } else {
                            deletedFileSystem.metadataFile.removeLibrary(deletedFileSystem.fsMetadata.id);
                        }
                    } catch (IOException e) {
                        log.warn(String.format("Unable to update DSS metadata after removal of file systelm '%s'", deletedFileSystem.fsMetadata.id), e);
                    }
                } else {
                    if (monitoredFilesIndex.getMonitoredPlugin(file) != null || monitoredFilesIndex.getMonitoredLibrary(file) != null) {
                        if (dssSettings.isBackgroundSynchronizationEnabled()) {
                            scheduleSynchronization(NOW);
                        }
                    } else {
                        // We need to enumerate all plugins & recipes & libraries to see if they are nested under the deleted directory, and act upon.
                        for (MonitoredRecipeFile nestedRecipeFile : monitoredFilesIndex.getMonitoredFilesNestedUnderDir(file)) {
                            monitoredFilesIndex.removeFromIndex(nestedRecipeFile);
                            try {
                                nestedRecipeFile.metadataFile.removeRecipe(nestedRecipeFile.recipe);
                            } catch (IOException e) {
                                log.warn(String.format("Unable to update DSS metadata after removal of file '%s'", file), e);
                            }
                        }

                        for (MonitoredPlugin nestedPlugin : monitoredFilesIndex.getMonitoredPluginsNestedUnderDir(file)) {
                            monitoredFilesIndex.removeFromIndex(nestedPlugin);
                            try {
                                nestedPlugin.metadataFile.removePlugin(nestedPlugin.plugin.pluginId);
                            } catch (IOException e) {
                                log.warn(String.format("Unable to update DSS metadata after removal of plugin '%s'", nestedPlugin.plugin.pluginId), e);
                            }
                        }
                        for (MonitoredLibrary nestedLib : monitoredFilesIndex.getMonitoredLibrariesNestedUnderDir(file)) {
                            monitoredFilesIndex.removeFromIndex(nestedLib);
                            try {
                                nestedLib.metadataFile.removeLibrary(nestedLib.library.projectKey);
                            } catch (IOException e) {
                                log.warn(String.format("Unable to update DSS metadata after removal of library form project '%s'", nestedLib.library.projectKey), e);
                            }
                        }
                    }
                }
            } else {
                MonitoredRecipeFile deletedRecipeFile = monitoredFilesIndex.getMonitoredFile(file);
                if (deletedRecipeFile != null) {
                    monitoredFilesIndex.removeFromIndex(deletedRecipeFile);
                    try {
                        deletedRecipeFile.metadataFile.removeRecipe(deletedRecipeFile.recipe);
                    } catch (IOException e) {
                        log.warn(String.format("Unable to update DSS metadata after removal of file '%s'", file), e);
                    }
                } else if (monitoredFilesIndex.getMonitoredPlugin(file) != null || monitoredFilesIndex.getMonitoredLibrary(file) != null) {
                    if (dssSettings.isBackgroundSynchronizationEnabled()) {
                        scheduleSynchronization(NOW);
                    }
                }
            }
        }

        @Override
        public void fileMoved(@NotNull VirtualFileMoveEvent event) {
            if (!dssSettings.isBackgroundSynchronizationEnabled()) {
                return;
            }
            if (monitoredFilesIndex.getMonitoredPlugin(event.getOldParent()) != null
                    || monitoredFilesIndex.getMonitoredPlugin(event.getNewParent()) != null ||
                    monitoredFilesIndex.getMonitoredLibrary(event.getOldParent()) != null
                    || monitoredFilesIndex.getMonitoredLibrary(event.getNewParent()) != null            ) {
                scheduleSynchronization(NOW);
            }
        }

        @Override
        public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
            if (!dssSettings.isBackgroundSynchronizationEnabled()) {
                return;
            }
            VirtualFile file = event.getFile();
            if (event.getPropertyName().equals("name")) {
                // File renamed
                String oldName = (String) event.getOldValue();
                String newName = (String) event.getNewValue();
                String path = file.getCanonicalPath();
                if (path != null && path.endsWith(newName)) {
                    String oldPath = path.substring(0, path.length() - newName.length()) + oldName;
                    MonitoredRecipeFile monitoredFile = monitoredFilesIndex.getMonitoredFile(oldPath);
                    if (monitoredFile != null) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            log.info(String.format("Detected rename operation on monitored recipe '%s'.", monitoredFile.recipe.recipeName));
                            syncRenamedRecipeFile(monitoredFile, file);
                        });
                    } else {
                        MonitoredPlugin monitoredPlugin = monitoredFilesIndex.getMonitoredPlugin(file);
                        MonitoredLibrary monitoredLibrary = monitoredFilesIndex.getMonitoredLibrary(file);
                        if (monitoredPlugin != null) {
                            log.info(String.format("Detected rename operation on file '%s' located inside monitored plugin directory.", file.getCanonicalPath()));
                            scheduleSynchronization(NOW);
                        } else if (monitoredLibrary != null) {
                            log.info(String.format("Detected rename operation on file '%s' located inside monitored library directory.", file.getCanonicalPath()));
                            scheduleSynchronization(NOW);

                        }
                    }
                }
            }
        }

        @Override
        public void contentsChanged(@NotNull VirtualFileEvent event) {
            if (!dssSettings.isBackgroundSynchronizationEnabled()) {
                return;
            }
            VirtualFile modifiedFile = event.getFile();
            MonitoredRecipeFile monitoredFile = monitoredFilesIndex.getMonitoredFile(modifiedFile);
            if (monitoredFile != null) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    log.info(String.format("Detected save operation on monitored file '%s'.", modifiedFile));
                    syncModifiedRecipeFile(monitoredFile);
                });
            } else {
                MonitoredPlugin monitoredPlugin = monitoredFilesIndex.getMonitoredPlugin(modifiedFile);
                MonitoredLibrary monitoredLibrary = monitoredFilesIndex.getMonitoredLibrary((modifiedFile));
                if (monitoredPlugin != null) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        log.info(String.format("Detected save operation on monitored file '%s'.", modifiedFile));
                        syncModifiedLibraryOrPluginFile(monitoredPlugin, modifiedFile);
                    });
                }
                if (monitoredLibrary != null) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        log.info(String.format("Detected save operation on monitored file '%s'.", modifiedFile));
                        syncModifiedLibraryOrPluginFile(monitoredLibrary, modifiedFile);
                    });
                }

            }
        }

        private void syncModifiedLibraryOrPluginFile(MonitoredFileSystem monitoredFS, VirtualFile modifiedFile) {
            String path = VirtualFileManager.getRelativePath(monitoredFS.baseDir, modifiedFile);
            DssFileMetadata trackedFile = monitoredFS.findFile(path);
            try {
                byte[] fileContent = ReadAction.compute(() -> VirtualFileManager.readVirtualFileAsByteArray(modifiedFile));
                if (trackedFile == null) {
                    // New file, send it to DSS
                    if (monitoredFS instanceof MonitoredPlugin) {
                        savePluginFileToDss(dssSettings, (MonitoredPlugin) monitoredFS, path, fileContent, true);
                    } else {
                        saveLibraryFileToDss(dssSettings, (MonitoredLibrary) monitoredFS, path, fileContent, true);
                    }

                } else if (getContentHash(fileContent) != trackedFile.contentHash) {
                    DssInstance dssInstance = dssSettings.getDssInstanceMandatory(monitoredFS.fsMetadata.instance);
                    DSSClient dssClient = dssInstance.createClient();

                    byte[] remoteData;
                    if (monitoredFS instanceof MonitoredPlugin) {
                        if(Strings.isNullOrEmpty(monitoredFS.fsMetadata.id)) {
                            monitoredFS.fsMetadata.id = ((MonitoredPlugin) monitoredFS).plugin.pluginId;
                        }
                        remoteData = dssClient.downloadPluginFile(monitoredFS.fsMetadata.id, trackedFile.remotePath);
                    } else {
                        String remoteDataString = dssClient.downloadLibraryFile(monitoredFS.fsMetadata.id, trackedFile.remotePath).data;
                        // Converting back to bytes to factorize code with plugin
                        if (Strings.isNullOrEmpty(remoteDataString)) {
                            remoteData = new byte[0];
                        } else {
                            remoteData = remoteDataString.getBytes(UTF_8);
                        }
                    }

                    int remoteHash = getContentHash(remoteData);
                    if (trackedFile.contentHash == remoteHash) {
                        log.info(String.format("File '%s' has been locally modified. Saving it onto the remote DSS instance", path));
                        if (monitoredFS instanceof MonitoredPlugin) {
                            savePluginFileToDss(dssSettings, (MonitoredPlugin) monitoredFS, path, fileContent, true);
                        } else {
                            saveLibraryFileToDss(dssSettings, (MonitoredLibrary) monitoredFS, path, fileContent, true);
                        }
                    } else {
                        // Conflict detected, run a global synchronization to correctly handle this corner-case.
                        scheduleSynchronization(NOW);
                    }
                }
            } catch (IOException e) {
                log.warn(String.format("Unable to synchronize plugin file '%s'.", path), e);
            }
        }

        private void syncModifiedRecipeFile(MonitoredRecipeFile monitoredFile) {
            try {
                String fileContent = ReadAction.compute(() -> VirtualFileManager.readVirtualFile(monitoredFile.file));
                if (getContentHash(fileContent) != monitoredFile.recipe.contentHash) {
                    DSSClient dssClient = dssSettings.getDssClient(monitoredFile.recipe.instance);
                    RecipeAndPayload remoteRecipe = dssClient.loadRecipe(monitoredFile.recipe.projectKey, monitoredFile.recipe.recipeName);
                    if (remoteRecipe != null) {
                        long remoteVersion = remoteRecipe.recipe.versionTag.versionNumber;
                        long localVersion = monitoredFile.recipe.versionNumber;
                        if (remoteVersion == localVersion) {
                            log.info(String.format("Recipe '%s' has been locally modified. Saving it onto the remote DSS instance", monitoredFile.recipe));
                            saveRecipeToDss(dssClient, monitoredFile, fileContent, true);
                        } else {
                            // Conflict detected, run a global synchronization to correctly handle this corner-case.
                            scheduleSynchronization(NOW);
                        }
                    } else {
                        // Conflict detected, run a global synchronization to correctly handle this corner-case.
                        scheduleSynchronization(NOW);
                    }
                }
            } catch (IOException e) {
                log.warn(String.format("Unable to synchronize recipe '%s'.", monitoredFile.recipe), e);
            }
        }

        private void syncRenamedRecipeFile(MonitoredRecipeFile monitoredFile, VirtualFile newFile) {
            try {
                String fileContent = ReadAction.compute(() -> VirtualFileManager.readVirtualFile(monitoredFile.file));
                if (getContentHash(fileContent) != monitoredFile.recipe.contentHash) {
                    log.info(String.format("File for recipe '%s' has been locally renamed. Recipe will not be renamed on DSS.", monitoredFile.recipe));
                    renameRecipeFile(monitoredFile, newFile, true);
                }
            } catch (IOException e) {
                log.warn(String.format("Unable to synchronize recipe '%s'.", monitoredFile.recipe), e);
            }
        }

    }

    private class DssSettingsListener implements DssSettings.Listener {
        @Override
        public void onConfigurationUpdated() {
            if (dssSettings.isBackgroundSynchronizationEnabled() && currentPollingInterval != dssSettings.getBackgroundSynchronizationPollIntervalInSeconds()) {
                scheduleSynchronization(INITIAL_DELAY);
            } else {
                cancelSynchronization();
            }
        }
    }
}
