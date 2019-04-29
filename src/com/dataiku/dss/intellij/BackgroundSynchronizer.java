package com.dataiku.dss.intellij;

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
import com.intellij.AppTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.VetoableProjectManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;

public class BackgroundSynchronizer implements ApplicationComponent {
    private static final Logger log = Logger.getInstance(BackgroundSynchronizer.class);

    private final MonitoredFilesIndex monitoredFilesIndex;
    private final DssSettings dssSettings;

    private ScheduledFuture<?> scheduledFuture;
    private SyncProjectManagerAdapter projectManagerAdapter;

    public BackgroundSynchronizer(DssSettings dssSettings, MonitoredFilesIndex monitoredFilesIndex) {
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

        // Every time a monitored file is saved in IntelliJ, upload it onto DSS.
        MessageBus bus = ApplicationManager.getApplication().getMessageBus();
        MessageBusConnection connection = bus.connect();
        connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new SyncFileDocumentManagerAdapter());

        projectManagerAdapter = new SyncProjectManagerAdapter();
        ProjectManager.getInstance().addProjectManagerListener(projectManagerAdapter);
    }

    @Override
    public void disposeComponent() {
        log.info("Stopping");

        ProjectManager.getInstance().removeProjectManagerListener(projectManagerAdapter);
        projectManagerAdapter = null;

        scheduledFuture.cancel(false);
    }

    private void runSynchronizer() {
        new SynchronizerWorker(dssSettings, newRecipeCache(), monitoredFilesIndex, true).run();
    }

    private void runDssToLocalSynchronizer() {
        new SynchronizerWorker(dssSettings, newRecipeCache(), monitoredFilesIndex, false).run();
    }

    private RecipeCache newRecipeCache() {
        return new RecipeCache(dssSettings);
    }

    private class SyncProjectManagerAdapter implements VetoableProjectManagerListener {
        @Override
        public void projectOpened(Project project) {
            runSynchronizer(); // Index all files present in newly opened project
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

    private class SyncFileDocumentManagerAdapter extends FileDocumentManagerAdapter {
        @Override
        public void beforeDocumentSaving(@NotNull Document document) {
            VirtualFile modifiedFile = FileDocumentManager.getInstance().getFile(document);
            if (modifiedFile != null) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    MonitoredFile monitoredFile = monitoredFilesIndex.getMonitoredFile(modifiedFile);
                    if (monitoredFile != null) {
                        log.info(String.format("Detected save operation on monitored file '%s'.", modifiedFile));
                        syncModifiedFile(monitoredFile);

                    }
                });
            }
        }

        private void syncModifiedFile(MonitoredFile monitoredFile) {
            try {
                String fileContent = ReadAction.compute(() -> VirtualFileUtils.readVirtualFile(monitoredFile.file));
                if (getContentHash(fileContent) != monitoredFile.recipe.contentHash) {
                    log.info(String.format("Recipe '%s' has been locally modified. Saving it onto the remote DSS server", monitoredFile.recipe));
                    saveRecipeToDss(dssSettings, monitoredFile, fileContent);
                }
            } catch (IOException e) {
                log.warn(String.format("Unable to synchronize recipe '%s'.", monitoredFile.recipe), e);
            }
        }
    }
}
