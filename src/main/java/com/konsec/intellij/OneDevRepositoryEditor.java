package com.konsec.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.util.Consumer;

public class OneDevRepositoryEditor extends BaseRepositoryEditor<OneDevRepository> {

    public OneDevRepositoryEditor(Project project, OneDevRepository repository, Consumer<? super OneDevRepository> changeListener) {
        super(project, repository, changeListener);
        this.myPasswordLabel.setText(TaskBundle.message("label.api.token"));

        this.myUserNameText.setVisible(false);
        this.myUsernameLabel.setVisible(false);
        this.myUseHttpAuthenticationCheckBox.setVisible(false);
    }
}