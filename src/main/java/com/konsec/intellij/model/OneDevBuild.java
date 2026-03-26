package com.konsec.intellij.model;

import java.util.Date;

public class OneDevBuild {
    public long id;
    public long number;
    public long projectId;
    public String jobName;
    public String status;
    public String commitHash;
    public String refName;
    public String version;
    public Date submitDate;
    public Date pendingDate;
    public Date runningDate;
    public Date finishDate;
}
