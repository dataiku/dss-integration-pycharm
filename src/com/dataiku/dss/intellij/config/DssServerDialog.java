package com.dataiku.dss.intellij.config;

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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import javax.swing.*;

import org.jetbrains.annotations.Nullable;

import com.dataiku.dss.Icons;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.ui.components.JBTextField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;

public class DssServerDialog extends DialogWrapper {

    private static final String API_KEY_PLACEHOLDER = "__DATAIKU_DSS_API_KEY_PLACEHOLDER__";

    private final DssServer server;
    private final Collection<String> existingNames;
    private JPanel panel;
    private JBTextField urlText;
    private JTextField nameField;
    private JPasswordField apiKeyField;
    private JCheckBox disableSslCertificateCheckBox;

    public DssServerDialog(DssServer serverToEdit) {
        this(serverToEdit, Collections.emptySet());
    }

    public DssServerDialog(Set<String> existingNames) {
        this(null, existingNames);
    }

    private DssServerDialog(DssServer serverToEdit, Set<String> existingNames) {
        super(true); // use current window as parent

        boolean editing = serverToEdit != null;
        this.existingNames = existingNames;

        server = new DssServer();
        if (editing) {
            server.name = serverToEdit.name;
            server.baseUrl = serverToEdit.baseUrl;
            server.encryptedApiKey = serverToEdit.encryptedApiKey;
            server.noCheckCertificate = serverToEdit.noCheckCertificate;
            server.readonly = serverToEdit.readonly;
        }

        init();
        setTitle((editing ? "Edit" : "New") + " Server");
        setOKActionEnabled(!server.readonly);
    }

    public DssServer getServer() {
        return server;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        panel = new JPanel(new GridLayoutManager(server.readonly ? 8 : 5, 2));
        panel.setPreferredSize(new Dimension(480, server.readonly ? 220 : 160));

        int lineIndex = 0;
        if (server.readonly) {
            JLabel label = new JLabel("This instance cannot be edited.");
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            label.setIcon(Icons.WARNING_16);
            panel.add(label, newColSpanConstraints(lineIndex++));
            panel.add(new JLabel("Its configuration is written in environment variables or in ~/.dataiku/config.json file."), newColSpanConstraints(lineIndex++));
            panel.add(new JSeparator(), newColSpanConstraints(lineIndex++));
        }

        panel.add(new JLabel("Name:"), newConstraints(lineIndex, 0, FILL_NONE, ANCHOR_WEST, SIZEPOLICY_FIXED));
        nameField = new JTextField();
        nameField.setMinimumSize(new Dimension(320, 28));
        nameField.setPreferredSize(new Dimension(320, 28));
        nameField.setToolTipText("Name of this instance (mandatory field)");
        panel.add(nameField, newConstraints(lineIndex, 1, GridConstraints.FILL_HORIZONTAL, ANCHOR_CENTER, SIZEPOLICY_WANT_GROW));
        lineIndex++;

        panel.add(new JLabel("Base URL:"), newConstraints(lineIndex, 0, FILL_NONE, ANCHOR_WEST, SIZEPOLICY_FIXED));
        urlText = new JBTextField();
        urlText.getEmptyText().setText("https://dss-instance:11200");
        urlText.setToolTipText("URL to connect to this instance (mandatory field)");
        urlText.setEnabled(!server.readonly);
        panel.add(urlText, newConstraints(lineIndex, 1, GridConstraints.FILL_HORIZONTAL, ANCHOR_CENTER, SIZEPOLICY_WANT_GROW));
        lineIndex++;

        panel.add(new JLabel("API Key:"), newConstraints(lineIndex, 0, FILL_NONE, ANCHOR_WEST, SIZEPOLICY_FIXED));
        apiKeyField = new JPasswordField();
        apiKeyField.setToolTipText("API key to connect to this instance (mandatory field)");
        apiKeyField.setEnabled(!server.readonly);
        panel.add(apiKeyField, newConstraints(lineIndex, 1, GridConstraints.FILL_HORIZONTAL, ANCHOR_CENTER, SIZEPOLICY_WANT_GROW));
        lineIndex++;

        disableSslCertificateCheckBox = new JCheckBox("Disable SSL certificate checks");
        disableSslCertificateCheckBox.setEnabled(!server.readonly);
        panel.add(disableSslCertificateCheckBox, newColSpanConstraints(lineIndex));
        lineIndex++;

        panel.add(new JSeparator(), newColSpanConstraints(lineIndex));

        fillFields();

        return this.panel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        if (nameField.isEnabled()) {
            return nameField;
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
        if (!isBlank(server.name)) {
            nameField.setText(server.name);
            nameField.setEnabled(false);
        }
        if (!isBlank(server.baseUrl)) {
            urlText.setText(server.baseUrl);
        }
        if (!isBlank(server.encryptedApiKey)) {
            apiKeyField.setText(API_KEY_PLACEHOLDER);
        }
        disableSslCertificateCheckBox.setSelected(server.noCheckCertificate);
    }

    private void commit() throws CommitStepException {
        validateName();
        validateUrl();
        save();
    }

    private void validateName() throws CommitStepException {
        String name = nameField.getText().trim();
        if (isBlank(name)) {
            throw new CommitStepException("Please provide a name.");
        }
        if (existingNames.contains(name)) {
            throw new CommitStepException("There is already a Dataiku DSS instance with that name. Please choose another name.");
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
        server.name = nameField.getText().trim();
        server.baseUrl = urlText.getText().trim();

        char[] apiKey = apiKeyField.getPassword();
        if (!Arrays.equals(API_KEY_PLACEHOLDER.toCharArray(), apiKey)) {
            server.encryptedApiKey = PasswordUtil.encodePassword(apiKey);
        }
        clearPasswordArray(apiKey);

        server.noCheckCertificate = disableSslCertificateCheckBox.isSelected();

        // Verify that we can connect to the server
        if (!checkConnection()) {
            throw new CommitStepException("Unable to connect to DSS using the provided URL and credentials.");
        }
    }

    private boolean checkConnection() throws CommitStepException {
        try {
            return server.createClient().canConnect();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * For security reasons, write zeros to overwrite the password
     */
    private static void clearPasswordArray(char[] password) {
        for (int i = 0; i < password.length; i++) {
            password[i] = 0;
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
