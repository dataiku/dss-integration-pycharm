package com.dataiku.dss.intellij.actions.synchronize;

import java.util.ArrayList;
import java.util.List;

import com.dataiku.dss.intellij.MonitoredFilesIndex;
import com.dataiku.dss.intellij.MonitoredPlugin;
import com.dataiku.dss.intellij.MonitoredRecipeFile;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeNodePlugin;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeNodeRecipe;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeNodeRoot;
import com.dataiku.dss.intellij.config.DssServer;
import com.dataiku.dss.intellij.config.DssSettings;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.wizard.AbstractWizardEx;
import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.openapi.project.Project;

class SynchronizeWizard {
    private SynchronizeModel model;
    private AbstractWizardEx wizard;

    SynchronizeWizard(Project project, MonitoredFilesIndex monitoredFilesIndex) {
        model = buildModel(monitoredFilesIndex);
        init(project, model);
    }

    boolean showAndGet() {
        return wizard.showAndGet();
    }

    SynchronizeModel getModel() {
        return model;
    }

    private void init(Project project, SynchronizeModel model) {
        List<AbstractWizardStepEx> steps = createSteps(model);
        wizard = new Wizard("Synchronize with DSS", project, steps);
    }

    private List<AbstractWizardStepEx> createSteps(SynchronizeModel model) {
        List<AbstractWizardStepEx> steps = new ArrayList<>();
        steps.add(new SynchronizeStep1(model));
        return steps;
    }

    private SynchronizeModel buildModel(MonitoredFilesIndex monitoredFilesIndex) {
        SynchronizeNodeRoot root = new SynchronizeNodeRoot();
        addRecipes(root, monitoredFilesIndex.getMonitoredRecipeFiles());
        addPlugins(root, monitoredFilesIndex.getMonitoredPlugins());

        SynchronizeModel result = new SynchronizeModel();
        result.selectionRootNode = root;
        return result;
    }

    private void addPlugins(SynchronizeNodeRoot root, List<MonitoredPlugin> plugins) {
        plugins.forEach(monitoredPlugin -> {
            DssServer dssInstance = DssSettings.getInstance().getDssServer(monitoredPlugin.plugin.instance);
            if (dssInstance != null) {
                SynchronizeNodePlugin pluginTreeNode = new SynchronizeNodePlugin(monitoredPlugin);
                root.getOrAddInstanceNode(dssInstance).getOrAddPluginsNode().add(pluginTreeNode);
            }
        });
    }

    private void addRecipes(SynchronizeNodeRoot root, List<MonitoredRecipeFile> monitoredFiles) {
        monitoredFiles.forEach(monitoredFile -> {
            DssServer dssInstance = DssSettings.getInstance().getDssServer(monitoredFile.recipe.instance);
            if (dssInstance != null) {
                root.getOrAddInstanceNode(dssInstance)
                        .getOrAddRecipesNode()
                        .getOrAddProjectNode(monitoredFile.recipe.projectKey)
                        .add(new SynchronizeNodeRecipe(monitoredFile));
            }
        });
    }

    private static class Wizard extends AbstractWizardEx {
        Wizard(String title, Project project, List<AbstractWizardStepEx> steps) {
            super(title, project, steps);
            this.setHorizontalStretch(1.25f);
            this.setVerticalStretch(1.25f);
            this.setOKButtonText("Synchronize");
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
}
