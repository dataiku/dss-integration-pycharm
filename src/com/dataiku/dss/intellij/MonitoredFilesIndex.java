package com.dataiku.dss.intellij;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.utils.ComponentUtils;
import com.dataiku.dss.intellij.utils.VirtualFileManager;
import com.dataiku.dss.model.metadata.DssPluginMetadata;
import com.dataiku.dss.model.metadata.DssRecipeMetadata;
import com.google.common.base.Preconditions;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;

public class MonitoredFilesIndex implements ApplicationComponent {
    private static final Logger log = Logger.getInstance(MonitoredFilesIndex.class);

    private final Map<String/*Path of file*/, MonitoredRecipeFile> monitoredRecipeFiles = new HashMap<>();
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
        index(ProjectManager.getInstance().getOpenProjects());
    }

    @Override
    public void disposeComponent() {
        monitoredRecipeFiles.clear();
        monitoredPlugins.clear();
    }

    public synchronized void index(VirtualFile monitoredFile, MetadataFile metadataFile, DssRecipeMetadata recipe) {
        Preconditions.checkNotNull(monitoredFile, "monitoredFile");
        Preconditions.checkNotNull(metadataFile, "metadataFile");
        Preconditions.checkNotNull(recipe, "recipe");
        index(new MonitoredRecipeFile(monitoredFile, metadataFile, recipe));
    }

    public synchronized void index(MonitoredRecipeFile monitoredFile) {
        Preconditions.checkNotNull(monitoredFile, "monitoredFile");
        log.info(String.format("Start tracking file '%s' corresponding to recipe '%s'.", monitoredFile.file, monitoredFile.recipe));
        monitoredRecipeFiles.put(monitoredFile.file.getCanonicalPath(), monitoredFile);
    }

    public synchronized void index(MonitoredPlugin monitoredPlugin) {
        Preconditions.checkNotNull(monitoredPlugin, "monitoredPlugin");
        log.info(String.format("Start tracking directory '%s' corresponding to plugin '%s'.", monitoredPlugin.pluginBaseDir, monitoredPlugin.plugin.pluginId));
        monitoredPlugins.put(monitoredPlugin.pluginBaseDir.getCanonicalPath(), monitoredPlugin);
    }

    public synchronized void removeFromIndex(MonitoredRecipeFile monitoredFile) {
        Preconditions.checkNotNull(monitoredFile, "monitoredFile");
        log.info(String.format("Stop tracking file '%s' corresponding to recipe '%s'.", monitoredFile.file, monitoredFile.recipe));
        monitoredRecipeFiles.remove(monitoredFile.file.getCanonicalPath());
    }

    public synchronized void removeFromIndex(MonitoredPlugin monitoredPlugin) {
        Preconditions.checkNotNull(monitoredPlugin, "monitoredPlugin");
        log.info(String.format("Stop tracking plugin directory '%s' corresponding to plugin on instance '%s'.", monitoredPlugin.pluginBaseDir, monitoredPlugin.plugin.instance));
        monitoredPlugins.remove(monitoredPlugin.pluginBaseDir.getCanonicalPath());
    }

    public synchronized MonitoredRecipeFile getMonitoredFile(VirtualFile file) {
        if (file == null) {
            return null;
        }
        return monitoredRecipeFiles.get(file.getCanonicalPath());
    }

    public synchronized MonitoredRecipeFile getMonitoredFile(String path) {
        if (path == null) {
            return null;
        }
        return monitoredRecipeFiles.get(path);
    }

    public synchronized List<MonitoredRecipeFile> getMonitoredRecipeFiles() {
        return new ArrayList<>(monitoredRecipeFiles.values());
    }

    public synchronized List<MonitoredPlugin> getMonitoredPlugins() {
        return new ArrayList<>(monitoredPlugins.values());
    }

    public synchronized void index(Project[] projects) {
        for (VirtualFile moduleContentRoot : listModulesRoot(projects)) {
            try {
                MetadataFile metadataFile = metadataFilesIndex.getMetadata(moduleContentRoot);
                if (metadataFile != null) {
                    for (DssRecipeMetadata recipe : metadataFile.metadata.recipes) {
                        VirtualFile recipeFile = moduleContentRoot.findFileByRelativePath(recipe.path);
                        if (recipeFile != null && recipeFile.isValid()) {
                            index(new MonitoredRecipeFile(recipeFile, metadataFile, recipe));
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

    public synchronized MonitoredPlugin getMonitoredPlugin(VirtualFile file) {
        for (MonitoredPlugin plugin : monitoredPlugins.values()) {
            String path = VirtualFileManager.getRelativePath(plugin.pluginBaseDir, file);
            if (path != null) {
                return plugin;
            }
        }
        return null;
    }

    public synchronized MonitoredPlugin getMonitoredPluginFromBaseDir(VirtualFile pluginBaseDir) {
        for (MonitoredPlugin plugin : monitoredPlugins.values()) {
            if (plugin.pluginBaseDir.getUrl().equals(pluginBaseDir.getUrl())) {
                return plugin;
            }
        }
        return null;
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
}
