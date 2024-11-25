package com.konsec.intellij;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskType;
import com.konsec.intellij.model.OneDevProject;
import com.konsec.intellij.model.OneDevTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;

public class OneDevTaskImpl extends Task implements Comparable<OneDevTaskImpl> {
    private final OneDevProject project;
    private final OneDevRepository repository;
    private Comment[] comments;
    final OneDevTask task;

    OneDevTaskImpl(@NotNull OneDevRepository repository, @NotNull OneDevTask task, @NotNull OneDevProject project) {
        this.repository = repository;
        this.task = task;
        this.project = project;
    }

    @NotNull
    @Override
    public String getId() {
        return project.name + "-" + task.number;
    }

    @Override
    public @NotNull String getNumber() {
        return String.valueOf(task.number);
    }

    @NotNull
    @Override
    public String getSummary() {
        return task.title;
    }

    @Nullable
    @Override
    public String getDescription() {
        return task.description;
    }

    @NotNull
    @Override
    public Comment @NotNull [] getComments() {
        if (comments == null) {
            try {
                comments = repository.getComments(this);
            } catch (Exception e) {
                //
            }
            if (comments == null) {
                comments = new Comment[0];
            }
        }
        return comments;
    }

    @NotNull
    @Override
    public Icon getIcon() {
        return IconLoader.getIcon("onedev.svg", OneDevRepository.class);
    }

    @NotNull
    @Override
    public TaskType getType() {
        return TaskType.OTHER;
    }

    @Nullable
    @Override
    public Date getUpdated() {
        var lastActivity = task.lastActivity;
        return lastActivity != null ? lastActivity.date : null;
    }

    @Nullable
    @Override
    public Date getCreated() {
        return task.submitDate;
    }

    @Override
    public boolean isClosed() {
        return "Closed".equals(task.state);
    }

    @Override
    public boolean isIssue() {
        return true;
    }

    @Override
    public @Nullable TaskRepository getRepository() {
        return repository;
    }

    @Nullable
    @Override
    public String getIssueUrl() {
        // http://127.0.0.1:6610/proj/~issues/1
        return repository.getUrl() + "/" + project.name + "/~issues/" + task.number;
    }

    @Override
    public int compareTo(@NotNull OneDevTaskImpl o) {
        return Integer.compare(this.task.id, o.task.id);
    }
}
