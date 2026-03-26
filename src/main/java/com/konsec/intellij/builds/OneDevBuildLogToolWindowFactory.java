package com.konsec.intellij.builds;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class OneDevBuildLogToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // Start empty — tabs are added dynamically when the user opens a build log
        var placeholder = new JLabel("Open a build from the \"OneDev Builds\" window to see its log.",
                SwingConstants.CENTER);
        var content = ContentFactory.getInstance().createContent(placeholder, "Logs", false);
        toolWindow.getContentManager().addContent(content);
    }
}
