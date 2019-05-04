package com.dataiku.dss.intellij.actions.checkin;

import java.util.List;
import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.dataiku.dss.intellij.actions.checkin.nodes.CheckinTree;
import com.dataiku.dss.intellij.actions.checkin.nodes.DssServerTreeNode;
import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.ide.wizard.CommitStepException;

public class CheckinStep1 extends AbstractWizardStepEx {
    private CheckinTree selectionTree;
    private JPanel mainPanel;

    public CheckinStep1(CheckinModel model) {
        super("");

        // If there is only one DSS instance, start the tree from this node, otherwise start from the root node.
        List<DssServerTreeNode> instanceNodes = model.synchronizeStepRootNode.getInstanceNodes();
        TreeNode rootNode = instanceNodes.size() == 1 ? instanceNodes.get(0) : model.synchronizeStepRootNode;
        selectionTree.setModel(new DefaultTreeModel(rootNode));
        selectionTree.setShowsRootHandles(false);
        expandAllNodes(selectionTree, 0, selectionTree.getRowCount());
    }

    private void expandAllNodes(JTree tree, int startingIndex, int rowCount) {
        for (int i = startingIndex; i < rowCount; ++i) {
            tree.expandRow(i);
        }

        if (tree.getRowCount() != rowCount) {
            expandAllNodes(tree, rowCount, tree.getRowCount());
        }
    }

    @NotNull
    @Override
    public Object getStepId() {
        return CheckinStep1.class;
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
}
