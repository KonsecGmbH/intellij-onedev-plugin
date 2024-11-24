package com.konsec.intellij;

import java.util.UUID;

public class AccessTokenDto {
    public int ownerId = 1;

    public String name = UUID.randomUUID().toString();

    public boolean hasOwnerPermissions = true;
}
