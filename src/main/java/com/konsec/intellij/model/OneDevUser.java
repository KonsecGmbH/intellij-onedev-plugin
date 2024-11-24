package com.konsec.intellij.model;

public class OneDevUser {
    private String id;
    private String name;
    private String fullName;

    public OneDevUser() {
        //
    }

    public String getLogin() {
        return id;
    }

    public void setLogin(String login) {
        this.id = login;
    }
}
