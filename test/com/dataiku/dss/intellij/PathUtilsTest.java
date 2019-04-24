package com.dataiku.dss.intellij;

import static org.junit.Assert.*;

import org.junit.Assert;
import org.junit.Test;

public class PathUtilsTest {
    @Test
    public void makeRelative1() throws Exception {
        Assert.assertEquals("PROJECT/foo.py", PathUtils.makeRelative("/users/jdoe/PROJECT/foo.py", "/users/jdoe"));
    }
    @Test
    public void makeRelative2() throws Exception {
        Assert.assertEquals("../PROJECT/foo.py", PathUtils.makeRelative("/users/jdoe/PROJECT/foo.py", "/users/jdoe/.dss"));
    }
}