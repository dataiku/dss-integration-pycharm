package com.dataiku.dss.intellij;

import static java.util.concurrent.TimeUnit.MINUTES;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.intellij.config.DssSettings;
import com.intellij.AppTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
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
    private ScheduledFuture<?> scheduledFuture;
    private SyncProjectManagerAdapter projectManagerAdapter;

    public BackgroundSynchronizer() {
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
        executorService.schedule(this::runGlobalSynchronizer, 0, MINUTES);

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

    private void runGlobalSynchronizer() {
        DssSettings dssSettings = DssSettings.getInstance();
        RecipeCache recipeCache = new RecipeCache(dssSettings);
        new GlobalSynchronizer(dssSettings, recipeCache).run();
    }

    private void runDssToLocalSynchronizer() {
        DssSettings dssSettings = DssSettings.getInstance();
        RecipeCache recipeCache = new RecipeCache(dssSettings);
        new DssToLocalSynchronizer(dssSettings, recipeCache).run();
    }

    private void runLocalToDssSynchronizer(VirtualFile modifiedFile) {
        DssSettings dssSettings = DssSettings.getInstance();
        RecipeCache recipeCache = new RecipeCache(dssSettings);
        new LocalToDssSynchronizer(dssSettings, recipeCache, modifiedFile).run();
    }

    private class SyncProjectManagerAdapter implements VetoableProjectManagerListener {

        @Override
        public void projectClosingBeforeSave(@NotNull Project project) {
            runGlobalSynchronizer();
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
                    System.out.println("Synchronize " + modifiedFile);
                    runLocalToDssSynchronizer(modifiedFile);
                });
            }
        }
    }
}
