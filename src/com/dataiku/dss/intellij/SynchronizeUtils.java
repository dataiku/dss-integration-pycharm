package com.dataiku.dss.intellij;

import static org.apache.commons.codec.Charsets.UTF_8;

import java.io.IOException;
import javax.swing.event.HyperlinkEvent;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.intellij.actions.synchronize.SynchronizeSummaryDialog;
import com.dataiku.dss.intellij.config.DssInstance;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.intellij.utils.VirtualFileManager;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.dss.RecipeAndPayload;
import com.dataiku.dss.model.metadata.DssPluginFileMetadata;
import com.dataiku.dss.model.metadata.DssRecipeMetadata;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class SynchronizeUtils {

    public static void savePluginFileToDss(DssSettings dssSettings, MonitoredPlugin monitoredPlugin, String path, byte[] fileContent, boolean flushMetadata) throws IOException {
        DSSClient dssClient = dssSettings.getDssClient(monitoredPlugin.plugin.instance);
        savePluginFileToDss(dssClient, monitoredPlugin, path, fileContent, flushMetadata);
    }

    public static void saveRecipeToDss(DssSettings dssSettings, MonitoredRecipeFile monitoredFile, String fileContent) throws IOException {
        DSSClient dssClient = dssSettings.getDssClient(monitoredFile.recipe.instance);
        saveRecipeToDss(dssClient, monitoredFile, fileContent, true);
    }

    public static void saveRecipeToDss(DssInstance dssInstance, MonitoredRecipeFile monitoredFile, String fileContent) throws IOException {
        saveRecipeToDss(dssInstance.createClient(), monitoredFile, fileContent, true);
    }

    public static void savePluginFileToDss(DSSClient dssClient, MonitoredPlugin monitoredPlugin, String path, byte[] fileContent, boolean flushMetadata) throws IOException {
        dssClient.uploadPluginFile(monitoredPlugin.plugin.pluginId, path, fileContent);
        DssPluginFileMetadata pluginFileMetadata = new DssPluginFileMetadata(
                monitoredPlugin.plugin.instance,
                monitoredPlugin.plugin.pluginId,
                monitoredPlugin.plugin.pluginId + "/" + path,
                path,
                VirtualFileManager.getContentHash(fileContent),
                fileContent);
        monitoredPlugin.metadataFile.addOrUpdatePluginFile(pluginFileMetadata, flushMetadata);
    }

    public static void saveRecipeToDss(DSSClient dssClient, MonitoredRecipeFile monitoredFile, String fileContent, boolean flushMetadata) throws IOException {
        // File has been updated locally, it needs to be sent to DSS.
        DssRecipeMetadata recipe = monitoredFile.recipe;
        RecipeAndPayload existingRecipe = dssClient.loadRecipe(recipe.projectKey, recipe.recipeName);
        if (existingRecipe != null && !fileContent.equals(existingRecipe.payload)) {
            dssClient.saveRecipeContent(recipe.projectKey, recipe.recipeName, fileContent);
            RecipeAndPayload updatedRecipe = dssClient.loadRecipe(recipe.projectKey, recipe.recipeName);

            // Update metadata & schedule associated metadata file to be updated
            recipe.versionNumber = updatedRecipe.recipe.versionTag.versionNumber;
            recipe.contentHash = VirtualFileManager.getContentHash(fileContent);
            recipe.data = fileContent.getBytes(UTF_8);

            if (flushMetadata) {
                monitoredFile.metadataFile.flush();
            }
        }
    }

    public static void renameRecipeFile(MonitoredRecipeFile monitoredFile, VirtualFile newFile, boolean flushMetadata) throws IOException {
        String oldName = monitoredFile.file.getName();
        String newName = newFile.getName();

        // Update metadata & schedule associated metadata file to be updated
        monitoredFile.file = newFile;
        monitoredFile.recipe.path = monitoredFile.recipe.path.substring(0, oldName.length()) + newName;

        if (flushMetadata) {
            monitoredFile.metadataFile.flush();
        }
    }

    public static void notifySynchronizationComplete(SynchronizeSummary summary, Project project) {
        Notification notification = new Notification("Dataiku DSS",
                "Synchronization with DSS completed",
                summary.getQuickSummary(),
                summary.conflicts.isEmpty() ? NotificationType.INFORMATION : NotificationType.WARNING,
                new SynchronizationNotificationListener(project, summary));
        Notifications.Bus.notify(notification, project);
    }

    public static void notifySynchronizationFailure(Exception e, Project project) {
        Notification notification = new Notification("Dataiku DSS",
                "Synchronization with DSS failed",
                e.getMessage(),
                NotificationType.ERROR);
        Notifications.Bus.notify(notification, project);
    }

    private static class SynchronizationNotificationListener extends NotificationListener.Adapter {
        private final Project project;
        private final SynchronizeSummary summary;

        SynchronizationNotificationListener(Project project, SynchronizeSummary summary) {
            this.project = project;
            this.summary = summary;
        }

        @Override
        protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
            new SynchronizeSummaryDialog(project, summary).showAndGet();
        }
    }

}
