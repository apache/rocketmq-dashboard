/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.dashboard.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class AclInfoTest {

    @Test
    public void testCopyFromUsesEmptyActionsWhenSourceActionsAreNull() {
        org.apache.rocketmq.remoting.protocol.body.AclInfo source = createSource(
            null, Collections.singletonList("127.0.0.1"));

        AclInfo target = new AclInfo();
        target.copyFrom(source);

        AclInfo.PolicyEntryInfo entry = target.getPolicies().get(0).getEntries().get(0);
        Assert.assertNotNull(entry.getActions());
        Assert.assertTrue(entry.getActions().isEmpty());
        Assert.assertEquals(Collections.singletonList("127.0.0.1"), entry.getSourceIps());
    }

    @Test
    public void testCopyFromUsesEmptySourceIpsWhenSourceIpsAreNull() {
        org.apache.rocketmq.remoting.protocol.body.AclInfo source = createSource(
            Collections.singletonList("PUB"), null);

        AclInfo target = new AclInfo();
        target.copyFrom(source);

        AclInfo.PolicyEntryInfo entry = target.getPolicies().get(0).getEntries().get(0);
        Assert.assertEquals(Collections.singletonList("PUB"), entry.getActions());
        Assert.assertNotNull(entry.getSourceIps());
        Assert.assertTrue(entry.getSourceIps().isEmpty());
    }

    @Test
    public void testCopyFromDefensivelyCopiesNonNullLists() {
        List<String> actions = new ArrayList<>(Collections.singletonList("PUB"));
        List<String> sourceIps = new ArrayList<>(Collections.singletonList("127.0.0.1"));
        org.apache.rocketmq.remoting.protocol.body.AclInfo source = createSource(actions, sourceIps);

        AclInfo target = new AclInfo();
        target.copyFrom(source);
        actions.add("SUB");
        sourceIps.add("127.0.0.2");

        AclInfo.PolicyEntryInfo entry = target.getPolicies().get(0).getEntries().get(0);
        Assert.assertEquals(Collections.singletonList("PUB"), entry.getActions());
        Assert.assertEquals(Collections.singletonList("127.0.0.1"), entry.getSourceIps());
    }

    private org.apache.rocketmq.remoting.protocol.body.AclInfo createSource(
        List<String> actions, List<String> sourceIps) {
        org.apache.rocketmq.remoting.protocol.body.AclInfo.PolicyEntryInfo entry =
            org.apache.rocketmq.remoting.protocol.body.AclInfo.PolicyEntryInfo.of(
                "Topic:test", actions, sourceIps, "ALLOW");
        org.apache.rocketmq.remoting.protocol.body.AclInfo.PolicyInfo policy =
            new org.apache.rocketmq.remoting.protocol.body.AclInfo.PolicyInfo();
        policy.setEntries(Collections.singletonList(entry));
        org.apache.rocketmq.remoting.protocol.body.AclInfo source =
            new org.apache.rocketmq.remoting.protocol.body.AclInfo();
        source.setSubject("User:test");
        source.setPolicies(Collections.singletonList(policy));
        return source;
    }
}
