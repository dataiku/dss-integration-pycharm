package com.dataiku.dss.intellij.actions.checkin;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.dataiku.dss.intellij.actions.checkin.nodes.CheckinTree;
import com.intellij.ide.wizard.AbstractWizardStepEx;
import com.intellij.ide.wizard.CommitStepException;

public class CheckinStep1 extends AbstractWizardStepEx {
    private CheckinTree tree1;
    private JPanel panel1;

    public CheckinStep1(CheckinModel model) {
        super("");
        tree1.setModel(new DefaultTreeModel(model.synchronizeStepRootNode));
        tree1.setShowsRootHandles(false);
        expandAllNodes(tree1, 0, tree1.getRowCount());
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
        return panel1;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return tree1;
    }
}
