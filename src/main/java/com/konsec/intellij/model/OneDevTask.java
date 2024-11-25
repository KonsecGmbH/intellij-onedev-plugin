package com.konsec.intellij.model;

import java.util.Date;

public class OneDevTask {
    public int projectId;
    public int id;
    public int number;
    public String title;
    public String description;
    public boolean confidential;
    public String state;
    public Date submitDate;
    public LastActivity lastActivity;

    public static class LastActivity {
        public Date date;
        public String description;
        public int userId;
    }
}
