package com.dataiku.dss.intellij.actions.synchronize.nodes;

import java.util.ArrayList;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

@SuppressWarnings("WeakerAccess")
public abstract class SynchronizeBaseNode extends DefaultMutableTreeNode {

    public SelectionState selectionState;

    public SynchronizeBaseNode() {
        this.selectionState = SelectionState.SELECTED;
    }

    public boolean isSelected() {
        return selectionState == SelectionState.SELECTED;
    }

    public void select() {
        selectionState = SelectionState.SELECTED;
        int childCount = getChildCount();
        for (int i = 0; i < childCount; ++i) {
            ((SynchronizeBaseNode) getChildAt(i)).select();
        }
        updateParentState();
    }

    public void toggle() {
        if (selectionState == SelectionState.SELECTED) {
            unselect();
        } else {
            select();
        }
    }

    public void unselect() {
        selectionState = SelectionState.NOT_SELECTED;
        int childCount = getChildCount();
        for (int i = 0; i < childCount; ++i) {
            ((SynchronizeBaseNode) getChildAt(i)).unselect();
        }
        updateParentState();
    }

    public List<SynchronizeBaseNode> listChildren() {
        return listChildren(SynchronizeBaseNode.class);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> listChildren(Class<T> clazz) {
        List<T> result = new ArrayList<>();
        int childCount = getChildCount();
        for (int i = 0; i < childCount; ++i) {
            TreeNode child = getChildAt(i);
            if (clazz.isAssignableFrom(child.getClass())) {
                result.add((T) child);
            }
        }
        return result;
    }

    protected SelectionState getChildrenAggregateState() {
        SelectionState aggState = null;
        for (SynchronizeBaseNode child : listChildren()) {
            if (aggState == null) {
                aggState = child.selectionState;
            } else if (aggState != child.selectionState) {
                return SelectionState.PARTLY_SELECTED;
            }
        }
        return aggState;
    }

    private void updateParentState() {
        SynchronizeBaseNode parent = (SynchronizeBaseNode) getParent();
        if (parent != null) {
            parent.selectionState = parent.getChildrenAggregateState();
            parent.updateParentState();
        }
    }
}
