package com.konsec.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.ui.components.*;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.GridBag;
import com.konsec.intellij.settings.OneDevServerProfile;
import com.konsec.intellij.settings.OneDevServerProfiles;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

// See JiraRepositoryEditor
public class OneDevRepositoryEditor extends BaseRepositoryEditor<OneDevRepository> {
    private static final String PROFILE_NONE = "<None>";

    private JBTextField mySearchQueryField;
    private JBLabel mySearchLabel;
    private JBCheckBox myUseAccessTokenAuthenticationCheckBox;
    private JBCheckBox myUseMutualTls;
    private JBTextField myMutualTlsFile;
    private JButton mySelectTlsFile;
    private JBPasswordField myMutualTlsPassword;

    private ComboBox<String> myProfileCombo;
    private JButton mySaveProfileButton;
    private JButton myDeleteProfileButton;
    private boolean myUpdatingProfiles;

    public OneDevRepositoryEditor(Project project, OneDevRepository repository, Consumer<? super OneDevRepository> changeListener) {
        super(project, repository, changeListener);

        myUseHttpAuthenticationCheckBox.setVisible(false);
    }

    @Override
    public void apply() {
        myRepository.setSearchQuery(mySearchQueryField.getText());
        myRepository.setUseAccessToken(myUseAccessTokenAuthenticationCheckBox.isSelected());

        myRepository.setUseMutualTls(myUseMutualTls.isSelected());
        myRepository.setMutualTlsCertificatePath(myMutualTlsFile.getText());
        myRepository.setMutualTlsCertificatePassword(new String(myMutualTlsPassword.getPassword()));

        super.apply();
        adjustSettingsForServerProperties();
    }

    @Override
    protected JComponent createCustomPanel() {
        mySearchQueryField = new JBTextField(myRepository.getSearchQuery());
        installListener(mySearchQueryField);
        mySearchLabel = new JBLabel(TaskBundle.message("label.search"), SwingConstants.RIGHT);
        myUseAccessTokenAuthenticationCheckBox = new JBCheckBox("Use access token");
        myUseAccessTokenAuthenticationCheckBox.addActionListener(e -> useAccessTokenChanged());
        myUseAccessTokenAuthenticationCheckBox.setSelected(myRepository.isUseAccessToken());
        installListener(myUseAccessTokenAuthenticationCheckBox);

        myUseMutualTls = new JBCheckBox("mTLS");
        myUseMutualTls.addActionListener(e -> useAccessTokenChanged());
        myUseMutualTls.setSelected(myRepository.isUseMutualTls());
        installListener(myUseMutualTls);
        myMutualTlsPassword = new JBPasswordField();
        myMutualTlsPassword.setText(myRepository.getMutualTlsCertificatePassword());
        myMutualTlsPassword.getEmptyText().setText("P12 password");
        installListener(myMutualTlsPassword);
        myMutualTlsFile = new JBTextField(myRepository.getMutualTlsCertificatePath());
        myMutualTlsFile.getEmptyText().setText("P12 path");
        installListener(myMutualTlsFile);
        mySelectTlsFile = new JButton("Select");
        mySelectTlsFile.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectPfxFile();
            }
        });

        JPanel mutualTlsPanel = new JPanel(new GridBagLayout());
        GridBag bag = new GridBag().setDefaultWeightX(1).setDefaultFill(GridBagConstraints.HORIZONTAL);
        mutualTlsPanel.add(myMutualTlsFile, bag.next().weightx(2));
        mutualTlsPanel.add(mySelectTlsFile, bag.next().fillCellNone().insets(0, 0, 0, 0).weightx(0));
        mutualTlsPanel.add(myMutualTlsPassword, bag.next());

        JPanel profilePanel = createProfilePanel();

        adjustSettingsForServerProperties();
        return FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Server profile", SwingConstants.RIGHT), profilePanel)
                .addComponentToRightColumn(myUseAccessTokenAuthenticationCheckBox)
                .addLabeledComponent(mySearchLabel, mySearchQueryField)
                .addLabeledComponent(myUseMutualTls, mutualTlsPanel)
                .getPanel();
    }

    private JPanel createProfilePanel() {
        myProfileCombo = new ComboBox<>();
        mySaveProfileButton = new JButton("Save as…");
        mySaveProfileButton.addActionListener(e -> saveCurrentAsProfile());
        myDeleteProfileButton = new JButton("Delete");
        myDeleteProfileButton.addActionListener(e -> deleteSelectedProfile());

        // Populate the combo only after all controls exist (refreshProfiles updates the delete button).
        refreshProfiles(PROFILE_NONE);
        myProfileCombo.addActionListener(e -> {
            if (myUpdatingProfiles) {
                return;
            }
            Object selected = myProfileCombo.getSelectedItem();
            myDeleteProfileButton.setEnabled(selected != null && !PROFILE_NONE.equals(selected));
            if (selected instanceof String name && !PROFILE_NONE.equals(name)) {
                applyProfileToForm(name);
            }
        });

        JPanel panel = new JPanel(new GridBagLayout());
        GridBag bag = new GridBag().setDefaultWeightX(1).setDefaultFill(GridBagConstraints.HORIZONTAL);
        panel.add(myProfileCombo, bag.next().weightx(2));
        panel.add(mySaveProfileButton, bag.next().fillCellNone().weightx(0));
        panel.add(myDeleteProfileButton, bag.next().fillCellNone().weightx(0));
        return panel;
    }

    private void refreshProfiles(String toSelect) {
        myUpdatingProfiles = true;
        try {
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            model.addElement(PROFILE_NONE);
            for (OneDevServerProfile profile : OneDevServerProfiles.getInstance().getProfiles()) {
                if (!StringUtil.isEmpty(profile.name)) {
                    model.addElement(profile.name);
                }
            }
            myProfileCombo.setModel(model);
            myProfileCombo.setSelectedItem(model.getIndexOf(toSelect) >= 0 ? toSelect : PROFILE_NONE);
        } finally {
            myUpdatingProfiles = false;
        }
        Object selected = myProfileCombo.getSelectedItem();
        myDeleteProfileButton.setEnabled(selected != null && !PROFILE_NONE.equals(selected));
    }

    private void applyProfileToForm(String name) {
        OneDevServerProfiles.getInstance().applyTo(name, myRepository);
        myURLText.setText(StringUtil.notNullize(myRepository.getUrl()));
        myUserNameText.setText(StringUtil.notNullize(myRepository.getUsername()));
        myPasswordText.setText(StringUtil.notNullize(myRepository.getPassword()));
        myUseAccessTokenAuthenticationCheckBox.setSelected(myRepository.isUseAccessToken());
        myUseMutualTls.setSelected(myRepository.isUseMutualTls());
        myMutualTlsFile.setText(StringUtil.notNullize(myRepository.getMutualTlsCertificatePath()));
        myMutualTlsPassword.setText(StringUtil.notNullize(myRepository.getMutualTlsCertificatePassword()));
        mySearchQueryField.setText(StringUtil.notNullize(myRepository.getSearchQuery()));
        adjustSettingsForServerProperties();
        doApply();
    }

    private void saveCurrentAsProfile() {
        Object selected = myProfileCombo.getSelectedItem();
        String initial = selected instanceof String s && !PROFILE_NONE.equals(s) ? s : StringUtil.notNullize(myRepository.getUrl());
        String name = Messages.showInputDialog(myProfileCombo, "Profile name:", "Save Server Profile", null, initial, null);
        if (StringUtil.isEmptyOrSpaces(name)) {
            return;
        }
        name = name.trim();
        // Capture the current form values into the repository, then snapshot them.
        apply();
        OneDevServerProfiles.getInstance().saveProfile(name, myRepository);
        refreshProfiles(name);
    }

    private void deleteSelectedProfile() {
        Object selected = myProfileCombo.getSelectedItem();
        if (selected instanceof String name && !PROFILE_NONE.equals(name)) {
            OneDevServerProfiles.getInstance().deleteProfile(name);
            refreshProfiles(PROFILE_NONE);
        }
    }

    @Override
    public void setAnchor(JComponent anchor) {
        super.setAnchor(anchor);
        mySearchLabel.setAnchor(anchor);
    }

    protected void useAccessTokenChanged() {
        myRepository.setUseAccessToken(myUseAccessTokenAuthenticationCheckBox.isSelected());
        myRepository.setUseMutualTls(myUseMutualTls.isSelected());
        adjustSettingsForServerProperties();
    }

    private void adjustSettingsForServerProperties() {
        if (myUseAccessTokenAuthenticationCheckBox.isSelected()) {
            myUsernameLabel.setVisible(false);
            myUserNameText.setVisible(false);
            myPasswordLabel.setText(TaskBundle.message("label.api.token"));
        } else {
            myUsernameLabel.setVisible(true);
            myUserNameText.setVisible(true);
            myUsernameLabel.setText(TaskBundle.message("label.username"));
            myPasswordLabel.setText(TaskBundle.message("label.password"));
        }

        if (myUseMutualTls.isSelected()) {
            myMutualTlsFile.setEnabled(true);
            myMutualTlsPassword.setEnabled(true);
            mySelectTlsFile.setEnabled(true);
        } else {
            myMutualTlsFile.setEnabled(false);
            myMutualTlsPassword.setEnabled(false);
            mySelectTlsFile.setEnabled(false);
        }
    }

    private void selectPfxFile() {
        var fc = new JFileChooser();
        if (JFileChooser.APPROVE_OPTION == fc.showOpenDialog(myCustomPanel)) {
            myMutualTlsFile.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }
}
