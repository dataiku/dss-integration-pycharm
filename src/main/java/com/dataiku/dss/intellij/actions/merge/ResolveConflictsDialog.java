package com.dataiku.dss.intellij.actions.merge;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.MonitoredLibrary;
import com.dataiku.dss.intellij.MonitoredPlugin;
import com.dataiku.dss.intellij.SynchronizeSummary;
import com.dataiku.dss.intellij.config.DssInstance;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.intellij.utils.VirtualFileManager;
import com.dataiku.dss.model.DSSClient;
import com.dataiku.dss.model.metadata.DssLibraryFileMetadata;
import com.dataiku.dss.model.metadata.DssPluginFileMetadata;
import com.google.common.base.Charsets;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.merge.MultipleFileMergeDialog;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.dataiku.dss.intellij.SynchronizeUtils.saveRecipeToDss;

public class ResolveConflictsDialog {
    private static final Logger log = Logger.getInstance(ResolveConflictsDialog.class);

    private final Project project;
    private final SynchronizeSummary summary;
    private final Map<String, MonitoredFileConflict> conflicts;

    public ResolveConflictsDialog(Project project, SynchronizeSummary summary) {
        this.project = project;
        this.summary = summary;
        this.conflicts = indexConflicts(summary);
    }

    public boolean showAndGet() {
        List<VirtualFile> virtualFiles = conflicts.values().stream().map(conflict -> conflict.file).collect(Collectors.toList());

        MergeDialogCustomizer mergeDialogCustomizer = new MergeDialogCustomizer();
        MultipleFileMergeDialog dialog = new MultipleFileMergeDialog(project, virtualFiles, new DssMergeProvider(), mergeDialogCustomizer);
        dialog.showAndGet();

        return !summary.hasConflicts();
    }

    private class DssMergeProvider implements MergeProvider {
        @NotNull
        @Override
        public MergeData loadRevisions(@NotNull VirtualFile virtualFile) throws VcsException {
            MonitoredFileConflict fileConflict = conflicts.get(virtualFile.getUrl());
            if (fileConflict == null) {
                throw new VcsException("Unable to find back file: " + virtualFile);
            }
            MergeData mergeData = new MergeData();
            mergeData.CURRENT = fileConflict.localData;
            mergeData.LAST = fileConflict.remoteData;
            mergeData.ORIGINAL = fileConflict.originalData;
            return mergeData;
        }

        @Override
        public void conflictResolvedForFile(@NotNull VirtualFile virtualFile) {
            log.info("conflictResolvedForFile " + virtualFile);
            MonitoredFileConflict monitoredFileConflict = conflicts.get(virtualFile.getUrl());
            if (monitoredFileConflict instanceof MonitoredRecipeFileConflict) {
                MonitoredRecipeFileConflict recipeFileConflict = (MonitoredRecipeFileConflict) monitoredFileConflict;
                try {
                    saveMergeRecipeFile(virtualFile, recipeFileConflict);
                    monitoredFileConflict.resolved = true;
                } catch (RuntimeException | IOException e) {
                    log.error("Unable to save merged recipe file.", e);
                    Messages.showDialog("Unable to save merged recipe file.", "I/O Error", new String[]{Messages.OK_BUTTON}, 0, null);
                }

            } else if (monitoredFileConflict instanceof MonitoredPluginFileConflict) {
                try {
                    saveMergePluginFile(virtualFile, (MonitoredPluginFileConflict) monitoredFileConflict);
                    monitoredFileConflict.resolved = true;
                } catch (RuntimeException | IOException e) {
                    log.error("Unable to save merged plugin file.", e);
                    Messages.showDialog("Unable to save merged plugin file.", "I/O Error", new String[]{Messages.OK_BUTTON}, 0, null);
                }
            } else if (monitoredFileConflict instanceof  MonitoredLibraryFileConflict) {
                try {
                    saveMergeLibraryFile(virtualFile, (MonitoredLibraryFileConflict) monitoredFileConflict);
                    monitoredFileConflict.resolved = true;
                } catch (RuntimeException | IOException e) {
                    log.error("Unable to save merged library file.", e);
                    Messages.showDialog("Unable to save merged library file.", "I/O Error", new String[]{Messages.OK_BUTTON}, 0, null);
                }
            }
        }

        @Override
        public boolean isBinary(@NotNull VirtualFile virtualFile) {
            return false;
        }
    }

    private void saveMergeRecipeFile(VirtualFile virtualFile, MonitoredRecipeFileConflict recipeFileConflict) throws IOException {
        String instanceName = recipeFileConflict.recipeFile.recipe.instance;
        DssInstance dssInstance = DssSettings.getInstance().getDssInstance(instanceName);
        if (dssInstance == null) {
            throw new IllegalStateException(String.format("Unknown DSS instance: %s", instanceName));
        }
        DSSClient dssClient = dssInstance.createClient();

        String mergedData = new String(VirtualFileManager.readVirtualFileAsByteArray(virtualFile), Charsets.UTF_8);
        saveRecipeToDss(dssClient, recipeFileConflict.recipeFile, mergedData, true);
    }

    private void saveMergePluginFile(@NotNull VirtualFile virtualFile, MonitoredPluginFileConflict pluginFileConflict) throws IOException {
        MonitoredPlugin plugin = pluginFileConflict.plugin;
        DssPluginFileMetadata pluginFile = pluginFileConflict.pluginFile;
        String instanceName = plugin.plugin.instance;
        DssInstance dssInstance = DssSettings.getInstance().getDssInstance(instanceName);
        if (dssInstance == null) {
            throw new IllegalStateException(String.format("Unknown DSS instance: %s", instanceName));
        }
        DSSClient dssClient = dssInstance.createClient();
        byte[] mergedData = VirtualFileManager.readVirtualFileAsByteArray(virtualFile);
        dssClient.uploadPluginFile(plugin.plugin.pluginId, pluginFile.path, mergedData);

        pluginFile.data = mergedData;
        pluginFile.contentHash = VirtualFileManager.getContentHash(mergedData);
        plugin.metadataFile.addOrUpdatePluginFile(pluginFile, true);
    }

    private void saveMergeLibraryFile(@NotNull VirtualFile virtualFile, MonitoredLibraryFileConflict libraryFileConflict) throws IOException {
        MonitoredLibrary library = libraryFileConflict.library;
        DssLibraryFileMetadata libraryFile = libraryFileConflict.libraryFile;
        String instanceName = library.library.instance;
        DssInstance dssInstance = DssSettings.getInstance().getDssInstance(instanceName);
        if (dssInstance == null) {
            throw new IllegalStateException(String.format("Unknown DSS instance: %s", instanceName));
        }
        DSSClient dssClient = dssInstance.createClient();
        byte[] mergedData = VirtualFileManager.readVirtualFileAsByteArray(virtualFile);
        dssClient.uploadLibraryFile(library.library.projectKey, libraryFile.path, mergedData);

        libraryFile.data = mergedData;
        libraryFile.contentHash = VirtualFileManager.getContentHash(mergedData);
        library.metadataFile.addOrUpdateLibraryFile(libraryFile, true);
    }


    private static Map<String, MonitoredFileConflict> indexConflicts(SynchronizeSummary summary) {
        Map<String, MonitoredFileConflict> result = new HashMap<>();
        summary.fileConflicts.forEach(conflict -> result.put(conflict.file.getUrl(), conflict));
        return result;
    }
}
