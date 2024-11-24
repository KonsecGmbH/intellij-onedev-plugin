package com.konsec.intellij;

import org.junit.Assert;
import org.junit.Test;

public class OneDevRepositoryTypeTest {
    private final OneDevRepositoryType repository = new OneDevRepositoryType();

    @Test
    public void getName() {
        Assert.assertNotNull(repository.getName());
    }

    @Test
    public void getIcon() {
        Assert.assertNotNull(repository.getIcon());
    }
}
