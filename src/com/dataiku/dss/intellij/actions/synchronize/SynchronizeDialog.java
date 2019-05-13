package com.dataiku.dss.intellij.actions.synchronize;

import java.util.List;
import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

import org.jetbrains.annotations.Nullable;

import com.dataiku.dss.intellij.MonitoredFilesIndex;
import com.dataiku.dss.intellij.MonitoredPlugin;
import com.dataiku.dss.intellij.MonitoredRecipeFile;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeNodeDssInstance;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeNodePlugin;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeNodeRecipe;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeNodeRoot;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeTree;
import com.dataiku.dss.intellij.config.DssServer;
import com.dataiku.dss.intellij.config.DssSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

public class SynchronizeDialog extends DialogWrapper {

    private final SynchronizeModel model;

    private SynchronizeTree selectionTree;
    private JPanel mainPanel;
    private JPanel selectionTreePanel;
    private JScrollPane selectionTreeScrollPane;
    private JSeparator separator;

    SynchronizeDialog(Project project, MonitoredFilesIndex monitoredFilesIndex) {
        super(project);
        model = buildModel(monitoredFilesIndex);
        // If there is only one DSS instance, start the tree from this node, otherwise start from the root node.
        List<SynchronizeNodeDssInstance> instanceNodes = model.selectionRootNode.getInstanceNodes();
        TreeNode rootNode = instanceNodes.size() == 1 ? instanceNodes.get(0) : model.selectionRootNode;
        selectionTree.setModel(new DefaultTreeModel(rootNode));
        selectionTree.setShowsRootHandles(false);
        selectionTree.setOpaque(true);
        selectionTreePanel.setOpaque(false);
        selectionTreePanel.setBorder(BorderFactory.createLineBorder(separator.getForeground(), 1));
        selectionTreeScrollPane.setOpaque(false);
        selectionTreeScrollPane.setBorder(BorderFactory.createEmptyBorder());
        expandAllNodes(selectionTree, 0, selectionTree.getRowCount());

        setOKButtonText("Synchronize");
        setTitle("Synchronize With DSS");
        setHorizontalStretch(1.25f);
        setVerticalStretch(1.25f);
        init();
    }

    SynchronizeModel getModel() {
        return model;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return mainPanel;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return selectionTree;
    }

    private void expandAllNodes(JTree tree, int startingIndex, int rowCount) {
        for (int i = startingIndex; i < rowCount; ++i) {
            tree.expandRow(i);
        }

        if (tree.getRowCount() != rowCount) {
            expandAllNodes(tree, rowCount, tree.getRowCount());
        }
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
}
