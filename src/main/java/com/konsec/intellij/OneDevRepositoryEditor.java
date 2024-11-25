package com.konsec.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskApiBundle;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;

import javax.swing.*;

// See JiraRepositoryEditor
public class OneDevRepositoryEditor extends BaseRepositoryEditor<OneDevRepository> {
    private JBTextField mySearchQueryField;
    private JBLabel mySearchLabel;
    private JCheckBox myUseAccessTokenAuthenticationCheckBox;

    public OneDevRepositoryEditor(Project project, OneDevRepository repository, Consumer<? super OneDevRepository> changeListener) {
        super(project, repository, changeListener);
        this.myPasswordLabel.setText(TaskBundle.message("label.api.token"));

        this.myUserNameText.setVisible(false);
        this.myUsernameLabel.setVisible(false);
    }

    @Override
    public void apply() {
        myRepository.setSearchQuery(mySearchQueryField.getText());
        myRepository.setUseAccessToken(myUseAccessTokenAuthenticationCheckBox.isSelected());
        super.apply();
    }

    @Override
    protected JComponent createCustomPanel() {
        mySearchQueryField = new JBTextField(myRepository.getSearchQuery());
        installListener(mySearchQueryField);
        mySearchLabel = new JBLabel(TaskBundle.message("label.search"), SwingConstants.RIGHT);
        myUseAccessTokenAuthenticationCheckBox = new JCheckBox(TaskApiBundle.message("use.personal.access.token"));
        myUseAccessTokenAuthenticationCheckBox.setSelected(myRepository.isUseAccessToken());
        myUseAccessTokenAuthenticationCheckBox.addActionListener(e -> useAccessTokenChanged());

        adjustSettingsForServerProperties();
        return FormBuilder.createFormBuilder()
                .addComponentToRightColumn(myUseAccessTokenAuthenticationCheckBox)
                .addLabeledComponent(mySearchLabel, mySearchQueryField)
                .getPanel();
    }

    protected void useAccessTokenChanged() {
        myRepository.setUseAccessToken(myUseAccessTokenAuthenticationCheckBox.isSelected());
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
    }
}
