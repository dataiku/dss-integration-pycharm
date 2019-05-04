package com.dataiku.dss.intellij.actions.synchronize;

import java.util.List;
import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeNodeDssInstance;
import com.dataiku.dss.intellij.actions.synchronize.nodes.SynchronizeTree;
import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.ide.wizard.CommitStepException;

public class SynchronizeStep1 extends AbstractWizardStepEx {
    private SynchronizeTree selectionTree;
    private JPanel mainPanel;

    SynchronizeStep1(SynchronizeModel model) {
        super("");

        // If there is only one DSS instance, start the tree from this node, otherwise start from the root node.
        List<SynchronizeNodeDssInstance> instanceNodes = model.selectionRootNode.getInstanceNodes();
        TreeNode rootNode = instanceNodes.size() == 1 ? instanceNodes.get(0) : model.selectionRootNode;
        selectionTree.setModel(new DefaultTreeModel(rootNode));
        selectionTree.setShowsRootHandles(false);
        expandAllNodes(selectionTree, 0, selectionTree.getRowCount());
    }

    @NotNull
    @Override
    public Object getStepId() {
        return SynchronizeStep1.class;
    }

    @Nullable
    @Override
    public Object getNextStepId() {
        return null;
    }

    @Nullable
    @Override
    public Object getPreviousStepId() {
        return null;
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public void commit(CommitType commitType) throws CommitStepException {
    }

    @Override
    public JComponent getComponent() {
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
}
