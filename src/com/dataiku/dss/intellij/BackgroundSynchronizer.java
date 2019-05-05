package com.dataiku.dss.intellij;

import static com.dataiku.dss.intellij.SynchronizerUtils.savePluginFileToDss;
import static com.dataiku.dss.intellij.SynchronizerUtils.saveRecipeToDss;
import static com.dataiku.dss.intellij.VirtualFileUtils.getContentHash;
import static java.util.concurrent.TimeUnit.MINUTES;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.config.DssSettings;
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
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

        // At startup, synchronize everything
        executorService.schedule(this::runSynchronizer, 0, MINUTES);

        // Then poll DSS every minute if one (or more) monitored recipes has been updated on DSS side.
        scheduledFuture = executorService.scheduleWithFixedDelay(this::runDssToLocalSynchronizer, 1, 1, MINUTES);

        projectManagerAdapter = new SyncProjectManagerAdapter();
        ProjectManager.getInstance().addProjectManagerListener(projectManagerAdapter);

        // Every time a monitored file is saved in IntelliJ, upload it onto DSS.
        LocalFileSystem.getInstance().addVirtualFileListener(new VirtualFileAdapter());
    }

    @Override
    public void disposeComponent() {
        log.info("Stopping");

        ProjectManager.getInstance().removeProjectManagerListener(projectManagerAdapter);
        projectManagerAdapter = null;

        scheduledFuture.cancel(false);
    }

    private void runSynchronizer() {
        //new SynchronizerWorker(dssSettings, newRecipeCache(), monitoredFilesIndex, true).run();
        SynchronizeRequest request = buildRequest(monitoredFilesIndex);
        if (!request.isEmpty()) {
            try {
                SynchronizeSummary summary = new SynchronizeWorker(dssPlugin, dssSettings, newRecipeCache()).synchronizeWithDSS(request);
                if (!summary.isEmpty()) {
                    SynchronizerUtils.notifySynchronizationComplete(summary, null);
                }
            } catch (IOException e) {
                SynchronizerUtils.notifySynchronizationFailure(e, null);
            }
        }
    }

    private SynchronizeRequest buildRequest(MonitoredFilesIndex monitoredFilesIndex) {
        return new SynchronizeRequest(monitoredFilesIndex.getMonitoredRecipeFiles(),
                monitoredFilesIndex.getMonitoredPlugins());
    }

    private void runDssToLocalSynchronizer() {
        runSynchronizer();
        //new SynchronizerWorker(dssSettings, newRecipeCache(), monitoredFilesIndex, false).run();
    }

    private RecipeCache newRecipeCache() {
        return new RecipeCache(dssSettings);
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
            System.out.println("File created");
            VirtualFile file = event.getFile();
        }

        @Override
        public void fileCopied(@NotNull VirtualFileCopyEvent event) {
            System.out.println("File copied");
            VirtualFile file = event.getFile();
        }

        @Override
        public void fileDeleted(@NotNull VirtualFileEvent event) {
            System.out.println("File deleted");
            VirtualFile file = event.getFile();
            MonitoredRecipeFile monitoredFile = monitoredFilesIndex.getMonitoredFile(file);
            if (monitoredFile != null) {
                monitoredFilesIndex.removeFromIndex(monitoredFile);
                try {
                    monitoredFile.metadataFile.removeRecipe(monitoredFile.recipe);
                } catch (IOException e) {
                    log.warn(String.format("Unable to update DSS metadata after removal of file '%s'", file), e);
                }
            }
        }

        @Override
        public void fileMoved(@NotNull VirtualFileMoveEvent event) {
            System.out.println("File moved");
            VirtualFile file = event.getFile();
        }

        @Override
        public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
            System.out.println("File propertyChanged");
            VirtualFile file = event.getFile();
            if (event.getPropertyName().equals("name")) {
                // File renamed
                String oldName = (String) event.getOldValue();
                System.out.println(oldName);
            }
        }

        @Override
        public void contentsChanged(@NotNull VirtualFileEvent event) {
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
    }
}
