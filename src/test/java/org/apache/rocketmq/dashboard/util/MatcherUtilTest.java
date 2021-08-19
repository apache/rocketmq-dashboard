package org.apache.rocketmq.dashboard.util;

import org.junit.Assert;
import org.junit.Test;

public class MatcherUtilTest {

    @Test
    public void testMatch() {
        boolean b = MatcherUtil.match("/topic/*.query", "/topic/route.query");
        boolean b1 = MatcherUtil.match("/**/**.do", "/consumer/route.do");
        boolean b2 = MatcherUtil.match("/*", "/topic/qqq/route.do");
        Assert.assertTrue(b);
        Assert.assertTrue(b1);
        Assert.assertFalse(b2);
    }
}

