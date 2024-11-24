package com.konsec.intellij.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OneDevTaskCreateData {
    public int projectId;
    public String title;
    public String description;
    public boolean confidential = false;
    public List<Long> iterationIds = new ArrayList<>();

    public Map<String, Serializable> fields = new HashMap<>();

    public int ownEstimatedTime = 0;
}
