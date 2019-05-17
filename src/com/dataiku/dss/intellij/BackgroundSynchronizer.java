package com.dataiku.dss.intellij;

import static com.dataiku.dss.intellij.SynchronizeUtils.renameRecipeFile;
import static com.dataiku.dss.intellij.SynchronizeUtils.savePluginFileToDss;
import static com.dataiku.dss.intellij.SynchronizeUtils.saveRecipeToDss;
import static com.dataiku.dss.intellij.utils.VirtualFileManager.getContentHash;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.intellij.utils.VirtualFileManager;
import com.dataiku.dss.model.metadata.DssPluginFileMetadata;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.VetoableProjectManagerListener;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileCopyEvent;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;

public class BackgroundSynchronizer implements ApplicationComponent {
    private static final Logger log = Logger.getInstance(BackgroundSynchronizer.class);
    private static final int INITIAL_DELAY = 10; // Wait for 10 seconds before triggering the first synchronization
    private static final int NOW = 0;

    private final MonitoredFilesIndex monitoredFilesIndex;
    private final DataikuDSSPlugin dssPlugin;
    private final DssSettings dssSettings;

    private SyncProjectManagerAdapter projectManagerAdapter;
    private ScheduledExecutorService executorService;
    private int currentPollingInterval = -1; // Negative if not scheduled
    private ScheduledFuture<?> scheduledFuture = null; // null if not scheduled
    private VirtualFileAdapter virtualFileAdapter;
    private DssSettingsListener dssSettingsListener;

    public BackgroundSynchronizer(DataikuDSSPlugin dssPlugin, DssSettings dssSettings, MonitoredFilesIndex monitoredFilesIndex) {
        this.dssPlugin = dssPlugin;
        this.dssSettings = dssSettings;
        this.monitoredFilesIndex = monitoredFilesIndex;
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
                        SynchronizeUtils.notifySynchronizationComplete(summary, null);
                    }
                } catch (IOException e) {
                    SynchronizeUtils.notifySynchronizationFailure(e, null);
                }
            }
        } catch (Exception e) {
            log.error("Caught exception while running synchronizer", e);
        }
    }

    private SynchronizeRequest buildRequest(MonitoredFilesIndex monitoredFilesIndex) {
        return new SynchronizeRequest(monitoredFilesIndex.getMonitoredRecipeFiles(),
                monitoredFilesIndex.getMonitoredPlugins());
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
        }

        @Override
        public void fileDeleted(@NotNull VirtualFileEvent event) {
            // We want to de-index files
            VirtualFile file = event.getFile();
            if (file.isDirectory()) {
                MonitoredPlugin deletedPlugin = monitoredFilesIndex.getMonitoredPluginFromBaseDir(file);
                if (deletedPlugin != null) {
                    monitoredFilesIndex.removeFromIndex(deletedPlugin);
                    try {
                        deletedPlugin.metadataFile.removePlugin(deletedPlugin.plugin.pluginId);
                    } catch (IOException e) {
                        log.warn(String.format("Unable to update DSS metadata after removal of plugin '%s'", deletedPlugin.plugin.pluginId), e);
                    }
                } else {
                    if (monitoredFilesIndex.getMonitoredPlugin(file) != null) {
                        if (dssSettings.isBackgroundSynchronizationEnabled()) {
                            scheduleSynchronization(NOW);
                        }
                    } else {
                        // We need to enumerate all plugins & recipes to see if they are nested under the deleted directory, and act upon.
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
                } else if (monitoredFilesIndex.getMonitoredPlugin(file) != null) {
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
                    || monitoredFilesIndex.getMonitoredPlugin(event.getNewParent()) != null) {
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
                        if (monitoredPlugin != null) {
                            log.info(String.format("Detected rename operation on file '%s' located inside monitored plugin directory.", file.getCanonicalPath()));
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
                if (monitoredPlugin != null) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        log.info(String.format("Detected save operation on monitored file '%s'.", modifiedFile));
                        syncModifiedPluginFile(monitoredPlugin, modifiedFile);
                    });
                }
            }
        }

        private void syncModifiedPluginFile(MonitoredPlugin monitoredPlugin, VirtualFile modifiedFile) {
            String path = VirtualFileManager.getRelativePath(monitoredPlugin.pluginBaseDir, modifiedFile);
            DssPluginFileMetadata trackedFile = monitoredPlugin.findFile(path);
            try {
                byte[] fileContent = ReadAction.compute(() -> VirtualFileManager.readVirtualFileAsByteArray(modifiedFile));
                if (trackedFile == null) {
                    // New file, send it to DSS
                    savePluginFileToDss(dssSettings, monitoredPlugin, path, fileContent, true);
                } else {
                    if (getContentHash(fileContent) != trackedFile.contentHash) {
                        log.info(String.format("Plugin file '%s' has been locally modified. Saving it onto the remote DSS instance", path));
                        savePluginFileToDss(dssSettings, monitoredPlugin, path, fileContent, true);
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
                    log.info(String.format("Recipe '%s' has been locally modified. Saving it onto the remote DSS instance", monitoredFile.recipe));
                    saveRecipeToDss(dssSettings, monitoredFile, fileContent);
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
