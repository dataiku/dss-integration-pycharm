package com.dataiku.dss.intellij;

import static com.dataiku.dss.intellij.SynchronizeUtils.renameRecipeFile;
import static com.dataiku.dss.intellij.SynchronizeUtils.savePluginFileToDss;
import static com.dataiku.dss.intellij.SynchronizeUtils.saveRecipeToDss;
import static com.dataiku.dss.intellij.utils.VirtualFileUtils.getContentHash;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.intellij.utils.VirtualFileUtils;
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

    private final MonitoredFilesIndex monitoredFilesIndex;
    private final DataikuDSSPlugin dssPlugin;
    private final DssSettings dssSettings;

    private ScheduledFuture<?> scheduledFuture;
    private SyncProjectManagerAdapter projectManagerAdapter;
    private ScheduledExecutorService executorService;

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
            scheduleSynchronization();
        }

        projectManagerAdapter = new SyncProjectManagerAdapter();
        ProjectManager.getInstance().addProjectManagerListener(projectManagerAdapter);

        // Every time a monitored file is saved in IntelliJ, upload it onto DSS.
        LocalFileSystem.getInstance().addVirtualFileListener(new VirtualFileAdapter());
    }

    private void scheduleSynchronization() {
        // Cancel existing scheduling
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        // Trigger a new one.
        long pollIntervalInSeconds = dssSettings.getBackgroundSynchronizationPollIntervalInSeconds();
        scheduledFuture = executorService.scheduleWithFixedDelay(this::runSynchronizer, 0, pollIntervalInSeconds, SECONDS);
    }

    @Override
    public void disposeComponent() {
        log.info("Stopping");

        ProjectManager.getInstance().removeProjectManagerListener(projectManagerAdapter);
        projectManagerAdapter = null;

        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
    }

    private void runSynchronizer() {
        SynchronizeRequest request = buildRequest(monitoredFilesIndex);
        if (!request.isEmpty()) {
            try {
                SynchronizeSummary summary = new SynchronizeWorker(dssPlugin, dssSettings, new RecipeCache(dssSettings)).synchronizeWithDSS(request);
                if (!summary.isEmpty()) {
                    SynchronizeUtils.notifySynchronizationComplete(summary, null);
                }
            } catch (IOException e) {
                SynchronizeUtils.notifySynchronizationFailure(e, null);
            }
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
            runSynchronizer();
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
                scheduleSynchronization();
            }
        }

        @Override
        public void fileCopied(@NotNull VirtualFileCopyEvent event) {
            if (!dssSettings.isBackgroundSynchronizationEnabled()) {
                return;
            }
            if (monitoredFilesIndex.getMonitoredPlugin(event.getOriginalFile()) != null
                    || monitoredFilesIndex.getMonitoredPlugin(event.getFile()) != null) {
                scheduleSynchronization();
            }
        }

        @Override
        public void fileDeleted(@NotNull VirtualFileEvent event) {
            if (!dssSettings.isBackgroundSynchronizationEnabled()) {
                return;
            }
            VirtualFile file = event.getFile();
            MonitoredRecipeFile monitoredFile = monitoredFilesIndex.getMonitoredFile(file);
            if (monitoredFile != null) {
                monitoredFilesIndex.removeFromIndex(monitoredFile);
                try {
                    monitoredFile.metadataFile.removeRecipe(monitoredFile.recipe);
                } catch (IOException e) {
                    log.warn(String.format("Unable to update DSS metadata after removal of file '%s'", file), e);
                }
            } else {
                if (monitoredFilesIndex.getMonitoredPlugin(event.getFile()) != null) {
                    scheduleSynchronization();
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
                scheduleSynchronization();
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
                            scheduleSynchronization();
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
            String path = VirtualFileUtils.getRelativePath(monitoredPlugin.pluginBaseDir, modifiedFile);
            DssPluginFileMetadata trackedFile = monitoredPlugin.findFile(path);
            try {
                byte[] fileContent = ReadAction.compute(() -> VirtualFileUtils.readVirtualFileAsByteArray(modifiedFile));
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
                String fileContent = ReadAction.compute(() -> VirtualFileUtils.readVirtualFile(monitoredFile.file));
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
                String fileContent = ReadAction.compute(() -> VirtualFileUtils.readVirtualFile(monitoredFile.file));
                if (getContentHash(fileContent) != monitoredFile.recipe.contentHash) {
                    log.info(String.format("File for recipe '%s' has been locally renamed. Recipe will not be renamed on DSS.", monitoredFile.recipe));
                    renameRecipeFile(monitoredFile, newFile, true);
                }
            } catch (IOException e) {
                log.warn(String.format("Unable to synchronize recipe '%s'.", monitoredFile.recipe), e);
            }
        }
    }
}
