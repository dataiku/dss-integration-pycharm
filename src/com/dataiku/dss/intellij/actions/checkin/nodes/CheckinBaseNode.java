package com.dataiku.dss.intellij.actions.checkin.nodes;

import java.util.ArrayList;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

public abstract class CheckinBaseNode extends DefaultMutableTreeNode {

    public SelectionState selectionState;

    public CheckinBaseNode() {
        this.selectionState = SelectionState.SELECTED;
    }

    public void select() {
        selectionState = SelectionState.SELECTED;
        int childCount = getChildCount();
        for (int i = 0; i < childCount; ++i) {
            ((CheckinBaseNode) getChildAt(i)).select();
        }
        updateParentState();
    }

    private void updateParentState() {
        CheckinBaseNode parent = (CheckinBaseNode) getParent();
        if (parent != null) {
            parent.selectionState = parent.getChildrenAggregateState();
            parent.updateParentState();
        }
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
            ((CheckinBaseNode) getChildAt(i)).unselect();
        }
        updateParentState();
    }

    public List<CheckinBaseNode> listChildren() {
        return listChildren(CheckinBaseNode.class);
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
        for (CheckinBaseNode child : listChildren()) {
            if (aggState == null) {
                aggState = child.selectionState;
            } else if (aggState != child.selectionState) {
                return SelectionState.PARTLY_SELECTED;
            }
        }
        if (aggState == null) {
            List<CheckinBaseNode> checkinBaseNodes = listChildren();
            System.out.println("Pouf");
        }
        return aggState;
    }
}
