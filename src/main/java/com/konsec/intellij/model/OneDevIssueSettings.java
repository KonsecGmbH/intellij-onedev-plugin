package com.konsec.intellij.model;

import java.util.ArrayList;
import java.util.List;

public class OneDevIssueSettings {
    public List<StateSpec> stateSpecs = new ArrayList<>();

    public static class StateSpec {
        public String name;
    }
}
