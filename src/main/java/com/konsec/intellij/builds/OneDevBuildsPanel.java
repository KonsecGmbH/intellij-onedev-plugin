package com.konsec.intellij.builds;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.TaskRepository;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.konsec.intellij.OneDevRepository;
import com.konsec.intellij.model.OneDevBuild;
import com.konsec.intellij.model.OneDevProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class OneDevBuildsPanel extends SimpleToolWindowPanel {
    private static final int AUTO_REFRESH_DELAY_SECONDS = 30;

    private final Project project;
    private final JBTable table;
    private final DefaultTableModel tableModel;
    private final JComboBox<ProjectItem> projectFilter;
    private boolean autoRefreshEnabled = true;
    private volatile ScheduledFuture<?> autoRefreshFuture;

    // Accessed only on EDT
    private List<OneDevBuild> currentBuilds = new ArrayList<>();
    private List<OneDevProject> currentProjects = new ArrayList<>();

    public OneDevBuildsPanel(@NotNull Project project) {
        super(true, true);
        this.project = project;

        tableModel = new DefaultTableModel(new String[]{"", "Job", "Project", "Branch", "Commit", "Date"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Icon.class : String.class;
            }
        };

        table = new JBTable(tableModel);
        table.getColumnModel().getColumn(0).setMaxWidth(24);
        table.getColumnModel().getColumn(0).setMinWidth(24);
        table.setShowGrid(false);
        table.setRowHeight(22);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0 && row < currentBuilds.size()) {
                        openBuildLog(currentBuilds.get(row));
                    }
                }
            }
        });

        projectFilter = new JComboBox<>();
        projectFilter.addItem(new ProjectItem(null, "All Projects"));
        projectFilter.addActionListener(e -> refresh());

        setToolbar(buildToolbar());
        setContent(new JBScrollPane(table));

        refresh();
    }

    private JComponent buildToolbar() {
        var refreshAction = new AnAction("Refresh", "Refresh builds list", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                refresh();
            }
        };

        var autoRefreshAction = new ToggleAction("Auto Refresh", "Toggle auto-refresh every 30 seconds",
                AllIcons.Actions.Execute) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return autoRefreshEnabled;
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                autoRefreshEnabled = state;
                if (state) {
                    scheduleAutoRefresh();
                } else {
                    cancelAutoRefresh();
                }
            }
        };

        var group = new DefaultActionGroup(refreshAction, autoRefreshAction);
        var actionToolbar = ActionManager.getInstance().createActionToolbar("OneDevBuilds", group, true);
        actionToolbar.setTargetComponent(this);

        var toolbarPanel = new JPanel(new BorderLayout());
        toolbarPanel.add(actionToolbar.getComponent(), BorderLayout.WEST);

        var filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        filterPanel.add(new JLabel("Project:"));
        filterPanel.add(projectFilter);
        toolbarPanel.add(filterPanel, BorderLayout.CENTER);

        return toolbarPanel;
    }

    @Nullable
    private OneDevRepository getRepository() {
        for (TaskRepository repo : TaskManager.getManager(project).getAllRepositories()) {
            if (repo instanceof OneDevRepository oneDevRepo) {
                return oneDevRepo;
            }
        }
        return null;
    }

    public void refresh() {
        cancelAutoRefresh();

        var repo = getRepository();
        if (repo == null) {
            return;
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                var projects = repo.loadProjects();

                ProjectItem selectedItem = projectFilter.getSelectedItem() instanceof ProjectItem item ? item : null;
                String query = buildQuery(projects, selectedItem);

                var builds = repo.loadBuilds(query, 0, OneDevRepository.MAX_COUNT);

                SwingUtilities.invokeLater(() -> updateTable(projects, builds, selectedItem));
            } catch (Exception ex) {
                // Silently ignore — server may be unavailable
            }
        });

        if (autoRefreshEnabled) {
            scheduleAutoRefresh();
        }
    }

    private String buildQuery(List<OneDevProject> projects, ProjectItem selectedItem) {
        if (selectedItem == null || selectedItem.id == null) {
            return "";
        }
        return projects.stream()
                .filter(p -> p.id == selectedItem.id)
                .findFirst()
                .map(p -> "\"Project\" is \"" + p.name + "\"")
                .orElse("");
    }

    private void updateTable(List<OneDevProject> projects, List<OneDevBuild> builds, ProjectItem previousSelection) {
        currentProjects = projects;
        currentBuilds = builds;

        // Refresh project filter without triggering actionListener
        var actionListeners = projectFilter.getActionListeners();
        for (var al : actionListeners) projectFilter.removeActionListener(al);

        projectFilter.removeAllItems();
        projectFilter.addItem(new ProjectItem(null, "All Projects"));
        for (var p : projects) {
            projectFilter.addItem(new ProjectItem((long) p.id, p.name));
        }

        // Restore selection
        if (previousSelection != null && previousSelection.id != null) {
            for (int i = 0; i < projectFilter.getItemCount(); i++) {
                if (projectFilter.getItemAt(i).equals(previousSelection)) {
                    projectFilter.setSelectedIndex(i);
                    break;
                }
            }
        }

        for (var al : actionListeners) projectFilter.addActionListener(al);

        tableModel.setRowCount(0);
        for (var build : builds) {
            var projectName = projects.stream()
                    .filter(p -> p.id == build.projectId)
                    .findFirst()
                    .map(p -> p.name)
                    .orElse(String.valueOf(build.projectId));

            String shortCommit = build.commitHash != null && build.commitHash.length() > 7
                    ? build.commitHash.substring(0, 7) : build.commitHash;

            tableModel.addRow(new Object[]{
                    statusIcon(build.status),
                    build.jobName,
                    projectName,
                    build.refName,
                    shortCommit,
                    build.submitDate != null ? formatRelativeDate(build.submitDate) : ""
            });
        }
    }

    private Icon statusIcon(String status) {
        if (status == null) return AllIcons.RunConfigurations.TestNotRan;
        return switch (status) {
            case "SUCCESSFUL" -> AllIcons.RunConfigurations.TestPassed;
            case "FAILED" -> AllIcons.RunConfigurations.TestFailed;
            case "CANCELLED", "TIMED_OUT" -> AllIcons.RunConfigurations.TestTerminated;
            case "RUNNING" -> AllIcons.RunConfigurations.TestNotRan;
            default -> AllIcons.RunConfigurations.TestNotRan;
        };
    }

    private String formatRelativeDate(java.util.Date date) {
        long diffMs = System.currentTimeMillis() - date.getTime();
        long minutes = diffMs / 60_000;
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        return (hours / 24) + "d ago";
    }

    private void openBuildLog(OneDevBuild build) {
        var toolWindowManager = ToolWindowManager.getInstance(project);
        var logWindow = toolWindowManager.getToolWindow("OneDev Build Log");
        if (logWindow == null) return;

        var repo = getRepository();
        if (repo == null) return;

        logWindow.activate(() -> {
            // Remove placeholder tab if present
            var contentManager = logWindow.getContentManager();
            if (contentManager.getContentCount() == 1) {
                var first = contentManager.getContent(0);
                if (first != null && !(first.getComponent() instanceof OneDevBuildLogPanel)) {
                    contentManager.removeContent(first, true);
                }
            }

            var panel = new OneDevBuildLogPanel(project, repo, build);
            var title = "#" + build.number + " " + build.jobName;
            var content = com.intellij.ui.content.ContentFactory.getInstance()
                    .createContent(panel, title, true);
            com.intellij.openapi.util.Disposer.register(content, panel);
            contentManager.addContent(content);
            contentManager.setSelectedContent(content);
        });
    }

    private void scheduleAutoRefresh() {
        cancelAutoRefresh();
        autoRefreshFuture = AppExecutorUtil.getAppScheduledExecutorService()
                .schedule(() -> SwingUtilities.invokeLater(this::refresh),
                        AUTO_REFRESH_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private void cancelAutoRefresh() {
        var future = autoRefreshFuture;
        if (future != null) {
            future.cancel(false);
            autoRefreshFuture = null;
        }
    }

    static class ProjectItem {
        final Long id;
        final String name;

        ProjectItem(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ProjectItem other)) return false;
            return Objects.equals(id, other.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }
    }
}
