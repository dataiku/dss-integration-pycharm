package com.dataiku.dss.intellij.config;

import static com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;
import static com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST;
import static com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL;
import static com.intellij.uiDesigner.core.GridConstraints.FILL_NONE;
import static com.intellij.uiDesigner.core.GridConstraints.FILL_VERTICAL;
import static com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW;
import static com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED;
import static com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.*;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.Icons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBPanel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;

public class DssSettingsPanel implements Disposable {
    private JPanel mainPanel;
    private JBList<DssServer> serverList;
    private final List<DssServer> servers = new ArrayList<>();
    private JSpinner pollingIntervalTextField;
    private JCheckBox automaticSynchronizationCheckBox;
    private Label pollingIntervalLabel1;
    private Label pollingIntervalLabel2;

    public DssSettingsPanel() {
    }

    private void create() {
        serverList = new JBList<>();
        serverList.getEmptyText().setText("No instances");
        serverList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    editServer();
                }
            }
        });
        serverList.setCellRenderer(new ColoredListCellRenderer<DssServer>() {
            protected void customizeCellRenderer(@NotNull JList<? extends DssServer> list, DssServer server, int index, boolean selected, boolean hasFocus) {
                setIcon(server.readonly ? Icons.BIRD_GRAY : Icons.BIRD_TEAL);
                append(" " + server.name, REGULAR_ATTRIBUTES);
                append(" " + server.baseUrl, GRAYED_ATTRIBUTES, false);
            }

            @NotNull
            @Override
            public Dimension getPreferredSize() {
                Dimension preferredSize = super.getPreferredSize();
                return new Dimension(preferredSize.width, Math.max(preferredSize.height, 24));
            }
        });

        JPanel serversPanel = new JPanel(new BorderLayout());
        ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(serverList);
        toolbarDecorator.setEditActionName("Edit").setEditAction((e) -> editServer());
        toolbarDecorator.setAddAction(new AddServerAction());
        toolbarDecorator.setRemoveAction(new RemoveServerAction());
        toolbarDecorator.setMoveUpAction(new MoveUpServerAction());
        toolbarDecorator.setMoveDownAction(new MoveDownServerAction());
        serversPanel.add(toolbarDecorator.createPanel(), "Center");
        JBPanel instancesPanel = new JBPanel(new BorderLayout()).withBorder(IdeBorderFactory.createTitledBorder("Instances"));
        instancesPanel.add(serversPanel);

        JPanel synchronizationPanel = new JBPanel(new BorderLayout()).withBorder(IdeBorderFactory.createTitledBorder("Synchronization"));
        JPanel synchronizationSubPanel = new JPanel(new GridLayoutManager(2, 1));
        synchronizationPanel.add(synchronizationSubPanel);
        automaticSynchronizationCheckBox = new JCheckBox("Automatic synchronization");

        synchronizationSubPanel.add(automaticSynchronizationCheckBox, newConstraints(0, 0, FILL_NONE, ANCHOR_WEST, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED));
        JBPanel pollingPanel = new JBPanel(new FlowLayout());
        pollingIntervalLabel1 = new Label("Poll DSS instances for changes every ");
        pollingIntervalLabel2 = new Label("seconds");
        pollingIntervalTextField = new JSpinner();
        pollingPanel.add(pollingIntervalLabel1);
        pollingPanel.add(pollingIntervalTextField);
        pollingPanel.add(pollingIntervalLabel2);
        synchronizationSubPanel.add(pollingPanel, newConstraints(1, 0, FILL_NONE, ANCHOR_WEST, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED));

        mainPanel = new JPanel(new GridLayoutManager(2, 1));
        mainPanel.add(synchronizationPanel, newConstraints(0, 0, FILL_HORIZONTAL, ANCHOR_WEST, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED));
        mainPanel.add(instancesPanel, newConstraints(1, 0, FILL_VERTICAL | FILL_HORIZONTAL, ANCHOR_WEST, SIZEPOLICY_CAN_GROW | SIZEPOLICY_WANT_GROW, SIZEPOLICY_CAN_GROW | SIZEPOLICY_WANT_GROW));

        automaticSynchronizationCheckBox.addActionListener(e -> {
            updatePollingIntervalState();
        });
    }

    private void updatePollingIntervalState() {
        boolean selected = automaticSynchronizationCheckBox.isSelected();
        pollingIntervalLabel1.setEnabled(selected);
        pollingIntervalTextField.setEnabled(selected);
        pollingIntervalLabel2.setEnabled(selected);
    }

    private static GridConstraints newConstraints(int row, int column, int fill, int anchor, int hSizePolicy, int vSizePolicy) {
        GridConstraints result = new GridConstraints();
        result.setRow(row);
        result.setColumn(column);
        result.setFill(fill);
        result.setAnchor(anchor);
        result.setHSizePolicy(hSizePolicy);
        result.setVSizePolicy(vSizePolicy);
        return result;
    }


    public JComponent getComponent() {
        if (mainPanel == null) {
            create();
        }
        return mainPanel;
    }

    public boolean isModified(@NotNull DssSettings settings) {
        return automaticSynchronizationCheckBox.isSelected() != settings.isBackgroundSynchronizationEnabled() ||
                getPollingIntervalValue() != settings.getBackgroundSynchronizationPollIntervalInSeconds() ||
                !servers.equals(settings.getDssServers());
    }

    private int getPollingIntervalValue() {
        Number value = (Number) pollingIntervalTextField.getValue();
        return Math.min(Math.max(10, value.intValue()), 3600);
    }

    public void save(@NotNull DssSettings settings) {
        settings.setDssServers(new ArrayList<>(servers));
        settings.setBackgroundSynchronizationEnabled(automaticSynchronizationCheckBox.isSelected());
        settings.setBackgroundSynchronizationPollIntervalInSeconds(getPollingIntervalValue());
    }

    public void load(@NotNull DssSettings settings) {
        servers.clear();
        CollectionListModel<DssServer> listModel = new CollectionListModel<>(new ArrayList<>());
        listModel.add(settings.getDssServers());
        servers.addAll(settings.getDssServers());
        serverList.setModel(listModel);
        if (!servers.isEmpty()) {
            serverList.setSelectedValue(servers.get(0), true);
        }
        automaticSynchronizationCheckBox.setSelected(settings.isBackgroundSynchronizationEnabled());
        pollingIntervalTextField.setModel(new SpinnerNumberModel(settings.getBackgroundSynchronizationPollIntervalInSeconds(), 10, 3600, 10));
        updatePollingIntervalState();
    }

    private DssServer getSelectedServer() {
        return serverList.getSelectedValue();
    }

    private void editServer() {
        DssServer selectedServer = getSelectedServer();
        int selectedIndex = serverList.getSelectedIndex();
        if (selectedServer != null) {
            DssServerDialog dialog = new DssServerDialog(selectedServer);
            if (dialog.showAndGet()) {
                DssServer newServer = dialog.getServer();
                ListModel<DssServer> model = serverList.getModel();
                ((CollectionListModel<DssServer>) model).setElementAt(newServer, selectedIndex);
                servers.set(servers.indexOf(selectedServer), newServer);
            }
        }
    }

    public void dispose() {
    }

    private class AddServerAction implements AnActionButtonRunnable {
        public void run(AnActionButton anActionButton) {
            Set<String> existingNames = servers.stream().map(DssServer::getName).collect(Collectors.toSet());
            DssServerDialog dialog = new DssServerDialog(existingNames);
            if (dialog.showAndGet()) {
                DssServer created = dialog.getServer();
                servers.add(created);
                ((CollectionListModel<DssServer>) serverList.getModel()).add(created);
                serverList.setSelectedIndex(serverList.getModel().getSize() - 1);
            }
        }
    }

    private class RemoveServerAction implements AnActionButtonRunnable {
        public void run(AnActionButton anActionButton) {
            DssServer server = getSelectedServer();
            int selectedIndex = serverList.getSelectedIndex();
            if (server != null) {
                if (server.readonly) {
                    Messages.showDialog("Its configuration is written in environment variables or in ~/.dataiku/config.json file.", "Cannot remove DSS instance.", new String[]{Messages.OK_BUTTON}, 0, null);
                    return;
                }
                CollectionListModel<DssServer> model = (CollectionListModel<DssServer>) serverList.getModel();
                model.remove(server);
                servers.remove(server);
                if (model.getSize() > 0) {
                    int newIndex = Math.min(model.getSize() - 1, Math.max(selectedIndex - 1, 0));
                    serverList.setSelectedValue(model.getElementAt(newIndex), true);
                }
            }
        }
    }

    private class MoveUpServerAction implements AnActionButtonRunnable {
        public void run(AnActionButton anActionButton) {
            DssServer server = getSelectedServer();
            int selectedIndex = serverList.getSelectedIndex();
            if (server != null && selectedIndex > 0) {
                CollectionListModel<DssServer> model = (CollectionListModel<DssServer>) serverList.getModel();
                int newIndex = selectedIndex - 1;
                model.remove(server);
                model.add(newIndex, server);

                servers.remove(server);
                servers.add(newIndex, server);

                serverList.setSelectedIndex(newIndex);
            }
        }
    }

    private class MoveDownServerAction implements AnActionButtonRunnable {
        public void run(AnActionButton anActionButton) {
            DssServer server = getSelectedServer();
            int selectedIndex = serverList.getSelectedIndex();
            if (server != null && selectedIndex < servers.size() - 1) {
                CollectionListModel<DssServer> model = (CollectionListModel<DssServer>) serverList.getModel();
                int newIndex = selectedIndex + 1;
                model.remove(server);
                model.add(newIndex, server);

                servers.remove(server);
                servers.add(newIndex, server);

                serverList.setSelectedIndex(newIndex);
            }
        }
    }
}
