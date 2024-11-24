package com.konsec.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class OneDevRepositoryType extends BaseRepositoryType<OneDevRepository> {
    @Override
    public @NotNull String getName() {
        return "OneDev";
    }

    @Override
    public @NotNull Icon getIcon() {
        return IconLoader.getIcon("onedev.svg", OneDevRepository.class);
    }

    @Override
    public @NotNull TaskRepository createRepository() {
        return new OneDevRepository();
    }

    @Override
    public Class<OneDevRepository> getRepositoryClass() {
        return OneDevRepository.class;
    }

    @NotNull
    @Override
    public TaskRepositoryEditor createEditor(OneDevRepository repository, Project project, Consumer<?super OneDevRepository> consumer) {
        return new OneDevRepositoryEditor(project, repository, consumer);
    }
}
