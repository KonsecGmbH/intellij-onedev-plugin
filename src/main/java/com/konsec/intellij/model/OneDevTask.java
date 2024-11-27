package com.konsec.intellij.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
    public List<Field> fields = new ArrayList<>();

    public String getType() {
        return fields.stream().filter(field -> "Type".equals(field.name))
                .map(field -> field.value)
                .findFirst()
                .orElse("");
    }

    public static class LastActivity {
        public Date date;
        public String description;
        public int userId;
    }

    public static class Field {
        public String name;
        public String value;
    }
}
