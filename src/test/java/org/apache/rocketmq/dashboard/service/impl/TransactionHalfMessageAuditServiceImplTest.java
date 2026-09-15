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

package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.dashboard.model.TransactionHalfMessageAuditReport;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TransactionHalfMessageAuditServiceImplTest {

    private TransactionHalfMessageAuditServiceImpl transactionHalfMessageAuditService;

    @Before
    public void setUp() {
        transactionHalfMessageAuditService = new TransactionHalfMessageAuditServiceImpl();
    }

    @Test
    public void testAuditPendingHalfMessages() {
        TransactionHalfMessageAuditReport report =
            transactionHalfMessageAuditService.auditPendingHalfMessages("BenchmarkTxTopic", "PG_TX_SERVICE");

        Assert.assertNotNull(report);
        Assert.assertEquals("BenchmarkTxTopic", report.getTopic());
        Assert.assertEquals("PG_TX_SERVICE", report.getProducerGroup());
        Assert.assertTrue(report.getTotalPendingHalfMessages() > 0);
        Assert.assertNotNull(report.getPendingHalfMessages());
        Assert.assertFalse(report.getPendingHalfMessages().isEmpty());
        Assert.assertNotNull(report.getHealthStatus());
        Assert.assertNotNull(report.getResolutionRecommendations());
    }

    @Test
    public void testResolveTransaction() {
        boolean commitRes = transactionHalfMessageAuditService.resolveTransaction("msg-01", "tx-01", "COMMIT");
        Assert.assertTrue(commitRes);

        boolean rollbackRes = transactionHalfMessageAuditService.resolveTransaction("msg-02", "tx-02", "ROLLBACK");
        Assert.assertTrue(rollbackRes);

        boolean invalidRes = transactionHalfMessageAuditService.resolveTransaction("", "tx-03", "COMMIT");
        Assert.assertFalse(invalidRes);
    }
}
