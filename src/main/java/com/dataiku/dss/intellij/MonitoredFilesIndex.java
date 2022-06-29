package com.dataiku.dss.intellij;

import com.dataiku.dss.Logger;
import com.dataiku.dss.intellij.utils.ComponentUtils;
import com.dataiku.dss.intellij.utils.VirtualFileManager;
import com.dataiku.dss.model.metadata.DssLibraryMetadata;
import com.dataiku.dss.model.metadata.DssPluginMetadata;
import com.dataiku.dss.model.metadata.DssRecipeMetadata;
import com.google.common.base.Preconditions;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MonitoredFilesIndex implements ApplicationComponent {
    private static final Logger log = Logger.getInstance(MonitoredFilesIndex.class);

    private final Map<String/*Path of file*/, MonitoredRecipeFile> monitoredRecipeFiles = new HashMap<>();
    private final Map<String/*Path of plugin base directory*/, MonitoredPlugin> monitoredPlugins = new HashMap<>();
    private final Map<String/*Path of plugin base directory*/, MonitoredLibrary> monitoredLibraries = new HashMap<>();
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
        log.info(String.format("Start tracking directory '%s' corresponding to plugin '%s'.", monitoredPlugin.baseDir, monitoredPlugin.plugin.pluginId));
        monitoredPlugins.put(monitoredPlugin.baseDir.getCanonicalPath(), monitoredPlugin);
    }

    public synchronized void index(MonitoredLibrary monitoredLibrary) {
        Preconditions.checkNotNull(monitoredLibrary, "monitoredPlugin");
        log.info(String.format("Start tracking directory '%s' corresponding to plugin '%s'.", monitoredLibrary.baseDir, monitoredLibrary.library.projectKey));
        monitoredLibraries.put(monitoredLibrary.baseDir.getCanonicalPath(), monitoredLibrary);
    }

    public synchronized void removeFromIndex(MonitoredRecipeFile monitoredFile) {
        Preconditions.checkNotNull(monitoredFile, "monitoredFile");
        log.info(String.format("Stop tracking file '%s' corresponding to recipe '%s'.", monitoredFile.file, monitoredFile.recipe));
        monitoredRecipeFiles.remove(monitoredFile.file.getCanonicalPath());
    }

    public synchronized void removeFromIndex(MonitoredPlugin monitoredPlugin) {
        Preconditions.checkNotNull(monitoredPlugin, "monitoredPlugin");
        log.info(String.format("Stop tracking plugin directory '%s' corresponding to plugin on instance '%s'.", monitoredPlugin.baseDir, monitoredPlugin.plugin.instance));
        monitoredPlugins.remove(monitoredPlugin.baseDir.getCanonicalPath());
    }

    public synchronized void removeFromIndex(MonitoredLibrary monitoredLib) {
        Preconditions.checkNotNull(monitoredLib, "monitoredLib");
        log.info(String.format("Stop tracking library directory '%s' corresponding to project on instance '%s'.", monitoredLib.baseDir, monitoredLib.library.instance));
        monitoredLibraries.remove(monitoredLib.baseDir.getCanonicalPath());
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

    public synchronized List<MonitoredLibrary> getMonitoredLibraries() {
        return new ArrayList<>(monitoredLibraries.values());
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
                    for (DssLibraryMetadata library : metadataFile.metadata.libraries) {
                        VirtualFile libraryBaseDir = moduleContentRoot.findFileByRelativePath(library.path);
                        if (libraryBaseDir != null && libraryBaseDir.isValid()) {
                            index(new MonitoredLibrary(libraryBaseDir, metadataFile, library));
                        }
                    }
                }
            } catch (RuntimeException e) {
                log.warn(String.format("Unable to index module '%s'.", moduleContentRoot), e);
            }
        }
    }

    public synchronized MonitoredLibrary getMonitoredLibrary(VirtualFile file) {
        for (MonitoredLibrary lib : monitoredLibraries.values()) {
            String path = VirtualFileManager.getRelativePath(lib.baseDir, file);
            if (path != null) {
                return lib;
            }
        }
        return null;
    }


    public synchronized MonitoredPlugin getMonitoredPlugin(VirtualFile file) {
        for (MonitoredPlugin plugin : monitoredPlugins.values()) {
            String path = VirtualFileManager.getRelativePath(plugin.baseDir, file);
            if (path != null) {
                return plugin;
            }
        }
        return null;
    }

    public synchronized MonitoredLibrary getMonitoredLibraryFromBaseDir(VirtualFile libBaseDir) {
        for (MonitoredLibrary lib : monitoredLibraries.values()) {
            if (lib.baseDir.getUrl().equals(libBaseDir.getUrl())) {
                return lib;
            }
        }
        return null;
    }

    public synchronized MonitoredPlugin getMonitoredPluginFromBaseDir(VirtualFile pluginBaseDir) {
        for (MonitoredPlugin plugin : monitoredPlugins.values()) {
            if (plugin.baseDir.getUrl().equals(pluginBaseDir.getUrl())) {
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

    public synchronized List<MonitoredRecipeFile> getMonitoredFilesNestedUnderDir(VirtualFile directory) {
        List<MonitoredRecipeFile> result = new ArrayList<>();

        for (MonitoredRecipeFile monitoredRecipeFile : monitoredRecipeFiles.values()) {
            String path = VirtualFileManager.getRelativePath(directory, monitoredRecipeFile.file);
            if (path != null) {
                result.add(monitoredRecipeFile);
            }
        }

        return result;
    }

    public synchronized List<MonitoredPlugin> getMonitoredPluginsNestedUnderDir(VirtualFile directory) {
        List<MonitoredPlugin> result = new ArrayList<>();

        for (MonitoredPlugin monitoredPlugin : monitoredPlugins.values()) {
            String path = VirtualFileManager.getRelativePath(directory, monitoredPlugin.baseDir);
            if (path != null) {
                result.add(monitoredPlugin);
            }
        }

        return result;
    }

    public synchronized List<MonitoredLibrary> getMonitoredLibrariesNestedUnderDir(VirtualFile directory) {
        List<MonitoredLibrary> result = new ArrayList<>();

        for (MonitoredLibrary monitoredLibrary : monitoredLibraries.values()) {
            String path = VirtualFileManager.getRelativePath(directory, monitoredLibrary.baseDir);
            if (path != null) {
                result.add(monitoredLibrary);
            }
        }
        return result;
    }

}

