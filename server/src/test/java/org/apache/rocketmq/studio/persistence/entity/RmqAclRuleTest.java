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
package org.apache.rocketmq.studio.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the stored ACL rule entity {@link RmqAclRule}: it maps to the {@code rmq_acl_rule}
 * table and every column round-trips - the persisted form of the access rule the UI edits,
 * with actions stored as one column rather than the view's list.
 */
class RmqAclRuleTest {

    @Test
    void theEntityMapsToTheAclRuleTable() {
        TableName annotation = RmqAclRule.class.getAnnotation(TableName.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("rmq_acl_rule");
    }

    @Test
    void everyColumnRoundTrips() {
        RmqAclRule rule = new RmqAclRule();
        rule.setId(7L);
        rule.setPrincipal("account-1");
        rule.setResource("order-*");
        rule.setResourceType("TOPIC");
        rule.setResourcePattern("LITERAL");
        rule.setActions("PUB,SUB");
        rule.setDecision("ALLOW");
        rule.setScope("instance");
        rule.setAclVersion("v2");

        assertThat(rule.getId()).isEqualTo(7L);
        assertThat(rule.getPrincipal()).isEqualTo("account-1");
        assertThat(rule.getResource()).isEqualTo("order-*");
        assertThat(rule.getResourceType()).isEqualTo("TOPIC");
        assertThat(rule.getResourcePattern()).isEqualTo("LITERAL");
        assertThat(rule.getActions()).isEqualTo("PUB,SUB");
        assertThat(rule.getDecision()).isEqualTo("ALLOW");
        assertThat(rule.getScope()).isEqualTo("instance");
        assertThat(rule.getAclVersion()).isEqualTo("v2");
    }
}
