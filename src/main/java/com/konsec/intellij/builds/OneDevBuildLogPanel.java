package com.konsec.intellij.builds;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLoadingPanel;
import com.konsec.intellij.OneDevBuildLogReader;
import com.konsec.intellij.OneDevRepository;
import com.konsec.intellij.model.OneDevBuild;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

public class OneDevBuildLogPanel extends SimpleToolWindowPanel implements Disposable {

    private final ConsoleView consoleView;
    private final JBLoadingPanel loadingPanel;
    private volatile InputStream logStream;
    private volatile boolean stopped = false;
    private volatile boolean loadingStopped = false;

    public OneDevBuildLogPanel(@NotNull Project project, @NotNull OneDevRepository repo,
                               @NotNull OneDevBuild build) {
        super(false, true);

        consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
        loadingPanel = new JBLoadingPanel(new BorderLayout(), this);
        loadingPanel.add(consoleView.getComponent());
        setContent(loadingPanel);

        var stopAction = new AnAction("Stop", "Stop streaming log", AllIcons.Actions.Suspend) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                stop();
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(!stopped);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };

        var toolbar = ActionManager.getInstance()
                .createActionToolbar("OneDevBuildLog", new DefaultActionGroup(stopAction), false);
        toolbar.setTargetComponent(consoleView.getComponent());
        setToolbar(toolbar.getComponent());

        loadingPanel.startLoading();
        ApplicationManager.getApplication().executeOnPooledThread(() -> streamLog(repo, build));
    }

    private void stopLoadingOnce() {
        if (!loadingStopped) {
            loadingStopped = true;
            SwingUtilities.invokeLater(loadingPanel::stopLoading);
        }
    }

    private void streamLog(OneDevRepository repo, OneDevBuild build) {
        try {
            logStream = repo.openBuildLogStream(build.id);
            OneDevBuildLogReader.read(logStream, new OneDevBuildLogReader.LogCallback() {
                @Override
                public void onLogEntry(Date date, List<OneDevBuildLogReader.LogMessage> messages) {
                    stopLoadingOnce();
                    if (stopped) return;
                    for (var msg : messages) {
                        consoleView.print(msg.text + "\n", contentTypeFor(msg.style));
                    }
                }

                @Override
                public void onStatusChange(String status) {
                    stopLoadingOnce();
                    if (stopped) return;
                    consoleView.print("[Status: " + status + "]\n", ConsoleViewContentType.SYSTEM_OUTPUT);
                }

                @Override
                public void onComplete() {
                    stopLoadingOnce();
                    stopped = true;
                    consoleView.print("[Log complete]\n", ConsoleViewContentType.SYSTEM_OUTPUT);
                }

                @Override
                public void onError(Exception e) {
                    stopLoadingOnce();
                    if (!stopped) {
                        consoleView.print("[Error reading log: " + e.getMessage() + "]\n",
                                ConsoleViewContentType.ERROR_OUTPUT);
                    }
                    stopped = true;
                }
            });
        } catch (IOException e) {
            stopLoadingOnce();
            if (!stopped) {
                consoleView.print("[Error: " + e.getMessage() + "]\n",
                        ConsoleViewContentType.ERROR_OUTPUT);
            }
        }
    }

    private ConsoleViewContentType contentTypeFor(String style) {
        if (style == null) return ConsoleViewContentType.NORMAL_OUTPUT;
        return switch (style.toLowerCase()) {
            case "error" -> ConsoleViewContentType.ERROR_OUTPUT;
            case "warning", "warn" -> ConsoleViewContentType.LOG_WARNING_OUTPUT;
            default -> ConsoleViewContentType.NORMAL_OUTPUT;
        };
    }

    public void stop() {
        stopped = true;
        var stream = logStream;
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void dispose() {
        stop();
        Disposer.dispose(consoleView);
    }
}
