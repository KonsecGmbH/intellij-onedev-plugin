package com.konsec.intellij.model;

public class OneDevProject {
    public int id;
    public String name;
    // Required @NotNull fields in newer OneDev versions (all internal fields are nullable, so empty objects are valid)
    public GitPackConfig gitPackConfig = new GitPackConfig();
    public CodeAnalysisSetting codeAnalysisSetting = new CodeAnalysisSetting();

    public static class GitPackConfig {}
    public static class CodeAnalysisSetting {}
}
