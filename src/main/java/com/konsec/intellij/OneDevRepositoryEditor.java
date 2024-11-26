package com.konsec.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.ui.components.*;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.GridBag;

import javax.swing.*;
import java.awt.*;

// See JiraRepositoryEditor
public class OneDevRepositoryEditor extends BaseRepositoryEditor<OneDevRepository> {
    private JBTextField mySearchQueryField;
    private JBLabel mySearchLabel;
    private JBCheckBox myUseAccessTokenAuthenticationCheckBox;
    private JBCheckBox myUseMutualTls;
    private JBTextField myMutualTlsFile;
    private JBPasswordField myMutualTlsPassword;

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
        myRepository.setMutualTlsCertificatePassword(myMutualTlsPassword.getText());

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

        JPanel mutualTlsPanel = new JPanel(new GridBagLayout());
        GridBag bag = new GridBag().setDefaultWeightX(1).setDefaultFill(GridBagConstraints.HORIZONTAL);
        mutualTlsPanel.add(myMutualTlsFile, bag.next().weightx(2));
        //mutualTlsPanel.add(new JLabel("/"), bag.next().fillCellNone().insets(0, 1, 0, 1).weightx(0));
        mutualTlsPanel.add(myMutualTlsPassword, bag.next());

        adjustSettingsForServerProperties();
        return FormBuilder.createFormBuilder()
                .addComponentToRightColumn(myUseAccessTokenAuthenticationCheckBox)
                .addLabeledComponent(mySearchLabel, mySearchQueryField)
                .addLabeledComponent(myUseMutualTls, mutualTlsPanel)
                .getPanel();
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
        } else {
            myMutualTlsFile.setEnabled(false);
            myMutualTlsPassword.setEnabled(false);
        }
    }
}
