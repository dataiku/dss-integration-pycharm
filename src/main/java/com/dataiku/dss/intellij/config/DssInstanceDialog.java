package com.dataiku.dss.intellij.config;

import static com.dataiku.dss.intellij.config.DssInstance.ENVIRONMENT_VARIABLE_INSTANCE_ID;
import static com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER;
import static com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST;
import static com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL;
import static com.intellij.uiDesigner.core.GridConstraints.FILL_NONE;
import static com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW;
import static com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED;
import static com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW;

import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;
import javax.swing.*;

import org.jetbrains.annotations.Nullable;

import com.dataiku.dss.Icons;
import com.dataiku.dss.Logger;
import com.dataiku.dss.model.DSSClient;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBTextField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;

public class DssInstanceDialog extends DialogWrapper {

    private static final String API_KEY_PLACEHOLDER = "__DATAIKU_DSS_API_KEY_PLACEHOLDER__";

    private static final Logger log = Logger.getInstance(DSSClient.class);

    private final DssInstance server;
    private final boolean readonly;
    private JPanel panel;
    private JBTextField urlText;
    private JTextField labelField;
    private JPasswordField apiKeyField;
    private JCheckBox disableSslCertificateCheckBox;

    public DssInstanceDialog() {
        this(null);
    }

    public DssInstanceDialog(DssInstance serverToEdit) {
        super(true); // use current window as parent

        boolean editing = serverToEdit != null;

        server = new DssInstance();
        if (editing) {
            server.id = serverToEdit.id;
            server.label = serverToEdit.label;
            server.baseUrl = serverToEdit.baseUrl;
            server.apiKey = serverToEdit.apiKey;
            server.noCheckCertificate = serverToEdit.noCheckCertificate;
            server.isDefault = serverToEdit.isDefault;
            readonly = ENVIRONMENT_VARIABLE_INSTANCE_ID.equals(server.id);
        } else {
            readonly = false;
        }

        init();
        setTitle((editing ? "Edit" : "New") + " Instance");
        setOKActionEnabled(!readonly);
    }

    public DssInstance getServer() {
        return server;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        panel = new JPanel(new GridLayoutManager(readonly ? 8 : 5, 2));
        panel.setPreferredSize(new Dimension(480, readonly ? 220 : 160));

        int lineIndex = 0;
        if (readonly) {
            JLabel label = new JLabel("This instance cannot be edited.");
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            label.setIcon(Icons.WARNING);
            panel.add(label, newColSpanConstraints(lineIndex++));
            panel.add(new JLabel("Its configuration has been set via environment variables."), newColSpanConstraints(lineIndex++));
            panel.add(new JSeparator(), newColSpanConstraints(lineIndex++));
        }

        panel.add(new JLabel("Display Name:"), newConstraints(lineIndex, 0, FILL_NONE, ANCHOR_WEST, SIZEPOLICY_FIXED));
        labelField = new JTextField();
        labelField.setMinimumSize(new Dimension(320, 28));
        labelField.setPreferredSize(new Dimension(320, 28));
        labelField.setToolTipText("Name of this instance (mandatory field)");
        panel.add(labelField, newConstraints(lineIndex, 1, GridConstraints.FILL_HORIZONTAL, ANCHOR_CENTER, SIZEPOLICY_WANT_GROW));
        lineIndex++;

        panel.add(new JLabel("Base URL:"), newConstraints(lineIndex, 0, FILL_NONE, ANCHOR_WEST, SIZEPOLICY_FIXED));
        urlText = new JBTextField();
        urlText.getEmptyText().setText("https://dss-instance:11200");
        urlText.setToolTipText("URL to connect to this instance (mandatory field)");
        urlText.setEnabled(!readonly);
        panel.add(urlText, newConstraints(lineIndex, 1, GridConstraints.FILL_HORIZONTAL, ANCHOR_CENTER, SIZEPOLICY_WANT_GROW));
        lineIndex++;

        panel.add(new JLabel("Personal API key secret:"), newConstraints(lineIndex, 0, FILL_NONE, ANCHOR_WEST, SIZEPOLICY_FIXED));
        apiKeyField = new JPasswordField();
        apiKeyField.setToolTipText("Personal API key secret to connect to this instance (mandatory field)");
        apiKeyField.setEnabled(!readonly);
        panel.add(apiKeyField, newConstraints(lineIndex, 1, GridConstraints.FILL_HORIZONTAL, ANCHOR_CENTER, SIZEPOLICY_WANT_GROW));
        lineIndex++;

        disableSslCertificateCheckBox = new JCheckBox("Disable SSL certificate checks");
        disableSslCertificateCheckBox.setEnabled(!readonly);
        panel.add(disableSslCertificateCheckBox, newColSpanConstraints(lineIndex));
        lineIndex++;

        panel.add(new JSeparator(), newColSpanConstraints(lineIndex));

        fillFields();

        return this.panel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        if (labelField.isEnabled()) {
            return labelField;
        } else {
            return urlText.isEnabled() ? urlText : null;
        }
    }

    @Override
    protected void doOKAction() {
        if (!isOKActionEnabled()) {
            return;
        }
        try {
            commit();
            super.doOKAction();
        } catch (CommitStepException e) {
            Messages.showErrorDialog(panel, e.getMessage(), "Invalid Configuration");
        }
    }

    private void fillFields() {
        if (!isBlank(server.label)) {
            labelField.setText(server.label);
        }
        if (!isBlank(server.baseUrl)) {
            urlText.setText(server.baseUrl);
        }
        if (!isBlank(server.apiKey)) {
            apiKeyField.setText(API_KEY_PLACEHOLDER);
        }
        disableSslCertificateCheckBox.setSelected(server.noCheckCertificate);
    }

    private void commit() throws CommitStepException {
        validateLabel();
        validateUrl();
        save();
    }

    private void validateLabel() throws CommitStepException {
        String name = labelField.getText().trim();
        if (isBlank(name)) {
            throw new CommitStepException("Please provide a label.");
        }
    }

    private void validateUrl() throws CommitStepException {
        try {
            URL url = new URL(urlText.getText());
            if (isBlank(url.getHost())) {
                throw new CommitStepException("Please provide a valid URL");
            }
        } catch (MalformedURLException e) {
            throw new CommitStepException("Please provide a valid URL");
        }
    }

    private void save() throws CommitStepException {
        server.label = labelField.getText().trim();
        server.baseUrl = urlText.getText().trim();

        String apiKey = new String(apiKeyField.getPassword());
        if (!API_KEY_PLACEHOLDER.equals(apiKey)) {
            server.apiKey = apiKey;
        }

        server.noCheckCertificate = disableSslCertificateCheckBox.isSelected();

        // Verify that we can connect to the server
        if (!checkConnection()) {
            throw new CommitStepException("Unable to connect to DSS using the provided URL and credentials.");
        }
    }

    private boolean checkConnection() {
        try {
            return server.createClient().canConnect();
        } catch (RuntimeException e) {
            log.info("Unable to connect to DSS", e);
            return false;
        }
    }

    private static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    private static GridConstraints newConstraints(int row, int column, int fill, int anchor, int hSizePolicy) {
        GridConstraints result = new GridConstraints();
        result.setRow(row);
        result.setColumn(column);
        result.setFill(fill);
        result.setAnchor(anchor);
        result.setHSizePolicy(hSizePolicy);
        return result;
    }

    private static GridConstraints newColSpanConstraints(int row) {
        GridConstraints result = newConstraints(row, 0, FILL_HORIZONTAL, ANCHOR_WEST, SIZEPOLICY_WANT_GROW | SIZEPOLICY_CAN_GROW);
        result.setColSpan(2);
        return result;
    }
}
