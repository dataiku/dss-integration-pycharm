package com.dataiku.dss.intellij;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.Logger;
import com.dataiku.dss.model.metadata.DssPluginMetadata;
import com.dataiku.dss.model.metadata.DssRecipeMetadata;
import com.google.common.base.Preconditions;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.VetoableProjectManagerListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;

public class MonitoredFilesIndex implements ApplicationComponent {
    private static final Logger log = Logger.getInstance(MonitoredFilesIndex.class);

    private final Map<String/*Path of file*/, MonitoredFile> monitoredFiles = new HashMap<>();
    private final Map<String/*Path of plugin base directory*/, MonitoredPlugin> monitoredPlugins = new HashMap<>();
    private final MetadataFilesIndex metadataFilesIndex;

    public static MonitoredFilesIndex getInstance() {
        return ComponentUtils.getComponent(MonitoredFilesIndex.class);
    }

    public MonitoredFilesIndex(MetadataFilesIndex metadataFilesIndex) {
        this.metadataFilesIndex = metadataFilesIndex;
    }

    @NotNull
    @Override
    public String getComponentName() {
        return "DSSMonitoredFilesIndex";
    }


    @Override
    public void initComponent() {
        // Scan all open projects, then register a listener to be called whenever a project is opened/closed
        LocalFileSystem.getInstance().addVirtualFileListener(new VirtualFileAdapter());
        index(ProjectManager.getInstance().getOpenProjects());
        ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter());
    }

    @Override
    public void disposeComponent() {
        monitoredFiles.clear();
        monitoredPlugins.clear();
    }

    public synchronized void index(VirtualFile monitoredFile, MetadataFile metadataFile, DssRecipeMetadata recipe) {
        Preconditions.checkNotNull(monitoredFile, "monitoredFile");
        Preconditions.checkNotNull(metadataFile, "metadataFile");
        Preconditions.checkNotNull(recipe, "recipe");

        index(new MonitoredFile(monitoredFile, metadataFile, recipe));
    }

    public synchronized void index(MonitoredFile monitoredFile) {
        Preconditions.checkNotNull(monitoredFile, "monitoredFile");
        log.info(String.format("Start tracking file '%s' corresponding to recipe '%s'.", monitoredFile.file, monitoredFile.recipe));
        monitoredFiles.put(monitoredFile.file.getCanonicalPath(), monitoredFile);
    }

    public synchronized void index(MonitoredPlugin monitoredPlugin) {
        Preconditions.checkNotNull(monitoredPlugin, "monitoredPlugin");
        log.info(String.format("Start tracking directory '%s' corresponding to plugin '%s'.", monitoredPlugin.pluginBaseDir, monitoredPlugin.plugin.pluginId));
        monitoredPlugins.put(monitoredPlugin.pluginBaseDir.getCanonicalPath(), monitoredPlugin);
    }

    public synchronized void removeFromIndex(MonitoredFile monitoredFile) {
        Preconditions.checkNotNull(monitoredFile, "monitoredFile");
        log.info(String.format("Stop tracking file '%s' corresponding to recipe '%s'.", monitoredFile.file, monitoredFile.recipe));
        monitoredFiles.remove(monitoredFile.file.getCanonicalPath());
    }

    public synchronized MonitoredFile getMonitoredFile(VirtualFile file) {
        if (file == null) {
            return null;
        }
        return monitoredFiles.get(file.getCanonicalPath());
    }

    public synchronized List<MonitoredFile> getMonitoredFiles() {
        return new ArrayList<>(monitoredFiles.values());
    }

    public synchronized List<MonitoredPlugin> getMonitoredPlugins() {
        return new ArrayList<>(monitoredPlugins.values());
    }

    private void index(Project[] projects) {
        for (VirtualFile moduleContentRoot : listModulesRoot(projects)) {
            try {
                MetadataFile metadataFile = metadataFilesIndex.getMetadata(moduleContentRoot);
                if (metadataFile != null) {
                    for (DssRecipeMetadata recipe : metadataFile.metadata.recipes) {
                        VirtualFile recipeFile = moduleContentRoot.findFileByRelativePath(recipe.path);
                        if (recipeFile != null && recipeFile.isValid()) {
                            index(new MonitoredFile(recipeFile, metadataFile, recipe));
                        }
                    }
                    for (DssPluginMetadata plugin : metadataFile.metadata.plugins) {
                        VirtualFile pluginBaseDir = moduleContentRoot.findFileByRelativePath(plugin.path);
                        if (pluginBaseDir != null && pluginBaseDir.isValid()) {
                            index(new MonitoredPlugin(pluginBaseDir, metadataFile, plugin));
                        }
                    }
                }
            } catch (RuntimeException e) {
                log.warn(String.format("Unable to index module '%s'.", moduleContentRoot), e);
            }
        }
    }

    private List<VirtualFile> listModulesRoot(Project[] projects) {
        Map<String, VirtualFile> result = new HashMap<>();
        for (Project project : projects) {
            for (VirtualFile moduleContentRoot : ProjectRootManager.getInstance(project).getContentRootsFromAllModules()) {
                String key = moduleContentRoot.getUrl();
                if (!result.containsKey(key)) {
                    result.put(key, moduleContentRoot);
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    private class VirtualFileAdapter implements VirtualFileListener {
        @Override
        public void fileDeleted(@NotNull VirtualFileEvent event) {
            VirtualFile file = event.getFile();
            MonitoredFile monitoredFile = getMonitoredFile(file);
            if (monitoredFile != null) {
                removeFromIndex(monitoredFile);
                try {
                    monitoredFile.metadataFile.removeRecipe(monitoredFile.recipe);
                } catch (IOException e) {
                    log.warn(String.format("Unable to update DSS metadata after removal of file '%s'", file), e);
                }
            }
        }

        @Override
        public void fileMoved(@NotNull VirtualFileMoveEvent event) {
            // TODO not handled yet
        }
    }

    private class ProjectManagerAdapter implements VetoableProjectManagerListener {
        @Override
        public boolean canClose(@NotNull Project project) {
            return true;
        }

        @Override
        public void projectOpened(Project project) {
            index(new Project[]{project});
        }

        @Override
        public void projectClosed(Project project) {

        }
    }
}
