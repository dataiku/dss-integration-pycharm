package com.dataiku.dss.intellij.actions.checkin;

import java.util.ArrayList;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

import com.dataiku.dss.intellij.MonitoredFile;
import com.dataiku.dss.intellij.MonitoredFilesIndex;
import com.dataiku.dss.intellij.MonitoredPlugin;
import com.dataiku.dss.intellij.actions.checkin.nodes.PluginFileTreeNode;
import com.dataiku.dss.intellij.actions.checkin.nodes.PluginFolderTreeNode;
import com.dataiku.dss.intellij.actions.checkin.nodes.PluginTreeNode;
import com.dataiku.dss.intellij.actions.checkin.nodes.RecipeTreeNode;
import com.dataiku.dss.intellij.actions.checkin.nodes.RootNode;
import com.dataiku.dss.intellij.config.DssServer;
import com.dataiku.dss.intellij.config.DssSettings;
import com.dataiku.dss.model.metadata.DssPluginFileMetadata;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.wizard.AbstractWizardEx;
import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.openapi.project.Project;

public class CheckinWizard {
    private CheckinModel model;
    private AbstractWizardEx wizard;

    public CheckinWizard(Project project, MonitoredFilesIndex monitoredFilesIndex) {
        model = buildModel(monitoredFilesIndex);
        init(project, model);
    }

    private void init(Project project, CheckinModel model) {
        List<AbstractWizardStepEx> steps = createSteps(project, model);
        wizard = new Wizard("Check in DSS Items", project, steps);
    }

    private static List<AbstractWizardStepEx> createSteps(Project project, CheckinModel model) {
        List<AbstractWizardStepEx> steps = new ArrayList<>();
        steps.add(new CheckinStep1(model));
        return steps;
    }

    private static class Wizard extends AbstractWizardEx {
        public Wizard(String title, Project project, List<AbstractWizardStepEx> steps) {
            super(title, project, steps);
            this.setHorizontalStretch(1.25f);
            this.setVerticalStretch(1.25f);
        }

        @Override
        protected void helpAction() {
            BrowserUtil.browse("https://doc.dataiku.com/dss/latest/python-api/outside-usage.html");
        }

        @Override
        protected String getDimensionServiceKey() {
            return this.getClass().getName();
        }
    }

    public boolean showAndGet() {
        return wizard.showAndGet();
    }

    public CheckinModel getModel() {
        return model;
    }

    private CheckinModel buildModel(MonitoredFilesIndex monitoredFilesIndex) {
        RootNode root = new RootNode();
        addRecipes(root, monitoredFilesIndex.getMonitoredFiles());
        addPlugins(root, monitoredFilesIndex.getMonitoredPlugins());

        CheckinModel result = new CheckinModel();
        result.synchronizeStepRootNode = root;
        return result;
    }

    private void addPlugins(RootNode root, List<MonitoredPlugin> plugins) {
        plugins.forEach(monitoredPlugin -> {
            DssServer dssInstance = DssSettings.getInstance().getDssServer(monitoredPlugin.plugin.instance);
            if (dssInstance != null) {
                PluginTreeNode pluginTreeNode = new PluginTreeNode(monitoredPlugin);
                root.getOrAddInstanceNode(dssInstance).getOrAddPluginsNode().add(pluginTreeNode);

                //monitoredPlugin.plugin.files.forEach(pluginFile -> addToPluginHierarchy(pluginTreeNode, pluginFile, pluginFile.remotePath.split("/")));
            }
        });
    }

    private void addRecipes(RootNode root, List<MonitoredFile> monitoredFiles) {
        monitoredFiles.forEach(monitoredFile -> {
            DssServer dssInstance = DssSettings.getInstance().getDssServer(monitoredFile.recipe.instance);
            if (dssInstance != null) {
                root.getOrAddInstanceNode(dssInstance)
                        .getOrAddRecipesNode()
                        .getOrAddProjectNode(monitoredFile.recipe.projectKey)
                        .add(new RecipeTreeNode(monitoredFile));
            }
        });
    }

    private void addToPluginHierarchy(DefaultMutableTreeNode parent, DssPluginFileMetadata pluginFile, String[] path) {
        if (path.length == 0) {
            throw new IllegalStateException("Zero-length path...");
        }
        if (path.length == 1) {
            parent.add(pluginFile.isFolder ? new PluginFolderTreeNode(pluginFile, path[0]) : new PluginFileTreeNode(pluginFile, path[0]));
        } else {
            PluginFolderTreeNode treeNode = getOrCreatePluginFolderTreeNode(parent, pluginFile, path[0]);
            String[] newPath = new String[path.length - 1];
            System.arraycopy(path, 1, newPath, 0, path.length - 1);
            addToPluginHierarchy(treeNode, pluginFile, newPath);
        }
    }

    private PluginFolderTreeNode getOrCreatePluginFolderTreeNode(DefaultMutableTreeNode parent, DssPluginFileMetadata pluginFile, String name) {
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TreeNode child = parent.getChildAt(i);
            if (child instanceof PluginFolderTreeNode) {
                PluginFolderTreeNode folderTreeNode = (PluginFolderTreeNode) child;
                if (name.equals(folderTreeNode.name)) {
                    return folderTreeNode;
                }
            }
        }

        // Not found, create one.
        PluginFolderTreeNode newPluginFolder = new PluginFolderTreeNode(pluginFile, name);
        parent.add(newPluginFolder);
        return newPluginFolder;
    }
}
