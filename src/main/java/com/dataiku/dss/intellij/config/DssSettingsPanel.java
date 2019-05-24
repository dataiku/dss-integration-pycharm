package com.dataiku.dss.intellij.config;

import static com.dataiku.dss.intellij.config.DssInstance.ENVIRONMENT_VARIABLE_INSTANCE_ID;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
import static com.intellij.uiDesigner.core.GridConstraints.ANCHOR_SOUTHEAST;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.*;

import org.jetbrains.annotations.NotNull;

import com.dataiku.dss.Icons;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
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
    private JBList<DssInstance> serverList;
    private final List<DssInstance> servers = new ArrayList<>();
    private DssInstance defaultInstance;
    private JSpinner pollingIntervalTextField;
    private JCheckBox automaticSynchronizationCheckBox;
    private JCheckBox usageReportingCheckBox;
    private Label pollingIntervalLabel1;
    private Label pollingIntervalLabel2;
    private JPanel serversPanel;

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
        serverList.setCellRenderer(new ColoredListCellRenderer<DssInstance>() {
            protected void customizeCellRenderer(@NotNull JList<? extends DssInstance> list, DssInstance server, int index, boolean selected, boolean hasFocus) {
                setIcon(Icons.BIRD_GRAY);
                append(" " + server.label, REGULAR_ATTRIBUTES);
                append("  " + server.baseUrl, GRAY_ATTRIBUTES);
                if (server.isDefault) {
                    append(" (Default)", REGULAR_BOLD_ATTRIBUTES);
                }
            }

            @NotNull
            @Override
            public Dimension getPreferredSize() {
                Dimension preferredSize = super.getPreferredSize();
                return new Dimension(preferredSize.width, Math.max(preferredSize.height, 24));
            }
        });

        serversPanel = new JPanel(new BorderLayout(10, 10));
        ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(serverList);
        toolbarDecorator.setEditActionName("Edit").setEditAction((e) -> editServer());
        toolbarDecorator.setAddAction(new AddServerAction());
        toolbarDecorator.setRemoveAction(new RemoveServerAction());
        if (servers.stream().noneMatch(s -> ENVIRONMENT_VARIABLE_INSTANCE_ID.equals(s.id))) {
            toolbarDecorator.addExtraAction(new MakeDefaultAction());
        }
        toolbarDecorator.setMoveUpAction(new MoveUpServerAction());
        toolbarDecorator.setMoveDownAction(new MoveDownServerAction());
        JLabel instanceNoticeLabel = new JLabel("Instances configurations are stored in ~/.dataiku/config.json");
        instanceNoticeLabel.setEnabled(false);
        Font instanceNoticeFont = instanceNoticeLabel.getFont();
        if (instanceNoticeFont != null) {
            long fontSmallSize = Math.round(instanceNoticeFont.getSize() * 0.8);
            instanceNoticeLabel.setFont(instanceNoticeFont.deriveFont((float) fontSmallSize));
        }

        serversPanel.add(instanceNoticeLabel, "South");
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

        JPanel otherPanel = new JBPanel(new BorderLayout()).withBorder(IdeBorderFactory.createTitledBorder("Other"));
        JPanel otherSubPanel = new JPanel(new GridLayoutManager(1, 2));
        otherPanel.add(otherSubPanel);
        usageReportingCheckBox = new JCheckBox("Usage reporting");
        otherSubPanel.add(usageReportingCheckBox, newConstraints(0, 0, FILL_NONE, ANCHOR_WEST, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED));

        JPanel noticePanel = new JPanel(new BorderLayout());
        JLabel notice = new JLabel("Submit usage information to Dataiku. Collected data is only used to help us improve DSS");
        notice.setEnabled(false);
        Font noticeFont = notice.getFont();
        if (noticeFont != null) {
            long fontSmallSize = Math.round(noticeFont.getSize() * 0.8);
            int gap = (int) (noticeFont.getSize() - fontSmallSize);
            notice.setFont(noticeFont.deriveFont((float) fontSmallSize));
            noticePanel.setBorder(BorderFactory.createEmptyBorder(gap / 2, 0, gap - (gap / 2), 0));
        }
        noticePanel.add(notice);
        otherSubPanel.add(noticePanel, newConstraints(0, 1, FILL_HORIZONTAL, ANCHOR_SOUTHEAST, SIZEPOLICY_WANT_GROW, SIZEPOLICY_FIXED));

        mainPanel = new JPanel(new GridLayoutManager(3, 1));
        mainPanel.add(synchronizationPanel, newConstraints(0, 0, FILL_HORIZONTAL, ANCHOR_WEST, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED));
        mainPanel.add(instancesPanel, newConstraints(1, 0, FILL_VERTICAL | FILL_HORIZONTAL, ANCHOR_WEST, SIZEPOLICY_CAN_GROW | SIZEPOLICY_WANT_GROW, SIZEPOLICY_CAN_GROW | SIZEPOLICY_WANT_GROW));
        mainPanel.add(otherPanel, newConstraints(2, 0, FILL_HORIZONTAL, ANCHOR_WEST, SIZEPOLICY_FIXED, SIZEPOLICY_FIXED));

        automaticSynchronizationCheckBox.addActionListener(e -> updatePollingIntervalState());
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
                !servers.equals(settings.getDssInstances()) ||
                !Objects.equals(defaultInstance, settings.getDefaultInstance()) ||
                usageReportingCheckBox.isSelected() != settings.isTrackingEnabled();
    }

    public void save(@NotNull DssSettings settings) {
        try {
            settings.updateConfig(
                    new ArrayList<>(servers),
                    defaultInstance,
                    automaticSynchronizationCheckBox.isSelected(),
                    getPollingIntervalValue(),
                    usageReportingCheckBox.isSelected());
        } catch (IOException e) {
            Messages.showErrorDialog("Unable to save ~/.dataiku/config.json. For more details, check the logs.", "Cannot Save Configuration");
        }
    }

    public void load(@NotNull DssSettings settings) {
        servers.clear();
        CollectionListModel<DssInstance> listModel = new CollectionListModel<>(new ArrayList<>());
        listModel.add(settings.getDssInstances());
        servers.addAll(settings.getDssInstances());
        serverList.setModel(listModel);
        if (!servers.isEmpty()) {
            serverList.setSelectedValue(servers.get(0), true);
        }
        defaultInstance = settings.getDefaultInstance();
        automaticSynchronizationCheckBox.setSelected(settings.isBackgroundSynchronizationEnabled());
        pollingIntervalTextField.setModel(new SpinnerNumberModel(settings.getBackgroundSynchronizationPollIntervalInSeconds(), 10, 3600, 10));
        updatePollingIntervalState();
        usageReportingCheckBox.setSelected(settings.isTrackingEnabled());
    }

    public void dispose() {
    }

    private int getPollingIntervalValue() {
        Number value = (Number) pollingIntervalTextField.getValue();
        return Math.min(Math.max(10, value.intValue()), 3600);
    }

    private DssInstance getSelectedServer() {
        return serverList.getSelectedValue();
    }

    private void editServer() {
        DssInstance selectedServer = getSelectedServer();
        int selectedIndex = serverList.getSelectedIndex();
        if (selectedServer != null) {
            DssInstanceDialog dialog = new DssInstanceDialog(selectedServer);
            if (dialog.showAndGet()) {
                DssInstance newServer = dialog.getServer();
                ListModel<DssInstance> model = serverList.getModel();
                ((CollectionListModel<DssInstance>) model).setElementAt(newServer, selectedIndex);
                servers.set(servers.indexOf(selectedServer), newServer);
            }
        }
    }

    private class AddServerAction implements AnActionButtonRunnable {
        public void run(AnActionButton anActionButton) {
            DssInstanceDialog dialog = new DssInstanceDialog();
            if (dialog.showAndGet()) {
                DssInstance created = dialog.getServer();
                created.id = findNextUniqueId(created.label);
                servers.add(created);
                ((CollectionListModel<DssInstance>) serverList.getModel()).add(created);
                if (servers.size() == 1) {
                    DssInstance instance = servers.get(0);
                    defaultInstance = instance;
                    instance.isDefault = true;
                }
                serverList.setSelectedIndex(serverList.getModel().getSize() - 1);
            }
        }

        private String findNextUniqueId(String label) {
            Set<String> existingIds = servers.stream().map(DssInstance::getId).collect(Collectors.toSet());
            String initialId = label.replaceAll("\\W", "-");
            String id = initialId;
            int index = 2;
            while (existingIds.contains(id)) {
                id = initialId + index;
                index++;
            }
            return id;
        }
    }

    private class RemoveServerAction implements AnActionButtonRunnable {
        public void run(AnActionButton anActionButton) {
            DssInstance server = getSelectedServer();
            int selectedIndex = serverList.getSelectedIndex();
            if (server != null) {
                if (ENVIRONMENT_VARIABLE_INSTANCE_ID.equals(server.id)) {
                    Messages.showDialog("Its configuration is written in environment variables.", "Cannot Remove DSS Instance.", new String[]{Messages.OK_BUTTON}, 0, null);
                    return;
                }
                CollectionListModel<DssInstance> model = (CollectionListModel<DssInstance>) serverList.getModel();
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
            DssInstance server = getSelectedServer();
            int selectedIndex = serverList.getSelectedIndex();
            if (server != null && selectedIndex > 0) {
                CollectionListModel<DssInstance> model = (CollectionListModel<DssInstance>) serverList.getModel();
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
            DssInstance server = getSelectedServer();
            int selectedIndex = serverList.getSelectedIndex();
            if (server != null && selectedIndex < servers.size() - 1) {
                CollectionListModel<DssInstance> model = (CollectionListModel<DssInstance>) serverList.getModel();
                int newIndex = selectedIndex + 1;
                model.remove(server);
                model.add(newIndex, server);

                servers.remove(server);
                servers.add(newIndex, server);

                serverList.setSelectedIndex(newIndex);
            }
        }
    }

    private class MakeDefaultAction extends AnActionButton {

        public MakeDefaultAction() {
            super("Make as Default", "Make this instance as Default DSS instance", AllIcons.Nodes.HomeFolder);
        }

        @Override
        public void actionPerformed(AnActionEvent anActionEvent) {
            DssInstance selectedServer = getSelectedServer();
            if (selectedServer != null) {
                servers.forEach(server -> server.isDefault = false);

                selectedServer.isDefault = true;
                defaultInstance = selectedServer;

                serversPanel.repaint();
            }
        }
    }
}
