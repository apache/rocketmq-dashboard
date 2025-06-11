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

package org.apache.rocketmq.dashboard.util;

import java.util.List;
import org.apache.rocketmq.client.trace.TraceConstants;
import org.apache.rocketmq.client.trace.TraceContext;
import org.apache.rocketmq.common.UtilAll;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class MsgTraceDecodeUtilTest {
    private StringBuilder pubTraceDataBase;
    private StringBuilder subTraceDataBase;

    @Before
    public void init() {
        pubTraceDataBase = new StringBuilder()
            .append("Pub").append(TraceConstants.CONTENT_SPLITOR)
            .append("1614663055253").append(TraceConstants.CONTENT_SPLITOR)
            .append("DefaultRegion").append(TraceConstants.CONTENT_SPLITOR)
            .append("DEFAULT_GROUP").append(TraceConstants.CONTENT_SPLITOR)
            .append("Trace_test").append(TraceConstants.CONTENT_SPLITOR)
            .append("0A741C02622500000000080cc6980189").append(TraceConstants.CONTENT_SPLITOR)
            .append(TraceConstants.CONTENT_SPLITOR)
            .append("123 456").append(TraceConstants.CONTENT_SPLITOR)
            .append("10.10.10.10:30911").append(TraceConstants.CONTENT_SPLITOR)
            .append("25").append(TraceConstants.CONTENT_SPLITOR)
            .append("1").append(TraceConstants.CONTENT_SPLITOR)
            .append("0").append(TraceConstants.CONTENT_SPLITOR);
        subTraceDataBase = new StringBuilder()
            .append("SubBefore").append(TraceConstants.CONTENT_SPLITOR)
            .append("1614666740499").append(TraceConstants.CONTENT_SPLITOR)
            .append(TraceConstants.CONTENT_SPLITOR)
            .append("test_consumer_group").append(TraceConstants.CONTENT_SPLITOR)
            .append("0A741C029C1800000000084501200121").append(TraceConstants.CONTENT_SPLITOR)
            .append("0A741C02622500000000080cc698003f").append(TraceConstants.CONTENT_SPLITOR)
            .append("2").append(TraceConstants.CONTENT_SPLITOR)
            .append("789 ").append(TraceConstants.CONTENT_SPLITOR)
            .append("10.10.10.11@39960").append(TraceConstants.CONTENT_SPLITOR)
            .append(TraceConstants.FIELD_SPLITOR)
            .append("SubAfter").append(TraceConstants.CONTENT_SPLITOR)
            .append("0A741C029C1800000000084501200121").append(TraceConstants.CONTENT_SPLITOR)
            .append("0A741C02622500000000080cc698003f").append(TraceConstants.CONTENT_SPLITOR)
            .append("0").append(TraceConstants.CONTENT_SPLITOR)
            .append("false").append(TraceConstants.CONTENT_SPLITOR)
            .append("789 ").append(TraceConstants.CONTENT_SPLITOR);
    }

    @Test
    public void testDecodePubTraceMessage() {
        List<TraceContext> contexts = MsgTraceDecodeUtil.decoderFromTraceDataString(null);
        Assert.assertEquals(0, contexts.size());
        String pubTraceDataV1 = new String(pubTraceDataBase);
        List<TraceContext> traceContextListV1 = MsgTraceDecodeUtil.decoderFromTraceDataString(pubTraceDataV1);
        Assert.assertEquals(1, traceContextListV1.size());
        Assert.assertEquals("Pub", traceContextListV1.get(0).getTraceType().toString());
        Assert.assertTrue(traceContextListV1.get(0).isSuccess());
        Assert.assertEquals("0A741C02622500000000080cc6980189", traceContextListV1.get(0).getTraceBeans().get(0).getMsgId());
        Assert.assertEquals("", traceContextListV1.get(0).getTraceBeans().get(0).getOffsetMsgId());
        Assert.assertEquals("10.10.10.10:30911", traceContextListV1.get(0).getTraceBeans().get(0).getStoreHost());
        Assert.assertEquals(traceContextListV1.get(0).getTraceBeans().get(0).getClientHost(), UtilAll.ipToIPv4Str(UtilAll.getIP()));

        String pubTraceDataV2 = new StringBuilder(pubTraceDataBase)
            .append("false").append(TraceConstants.CONTENT_SPLITOR)
            .toString();
        List<TraceContext> traceContextListV2 = MsgTraceDecodeUtil.decoderFromTraceDataString(pubTraceDataV2);
        Assert.assertEquals(1, traceContextListV2.size());
        Assert.assertEquals("Pub", traceContextListV2.get(0).getTraceType().toString());
        Assert.assertFalse(traceContextListV2.get(0).isSuccess());
        Assert.assertEquals("0A741C02622500000000080cc6980189", traceContextListV2.get(0).getTraceBeans().get(0).getMsgId());
        Assert.assertEquals("", traceContextListV2.get(0).getTraceBeans().get(0).getOffsetMsgId());
        Assert.assertEquals("10.10.10.10:30911", traceContextListV2.get(0).getTraceBeans().get(0).getStoreHost());
        Assert.assertEquals(traceContextListV2.get(0).getTraceBeans().get(0).getClientHost(), UtilAll.ipToIPv4Str(UtilAll.getIP()));

        String pubTraceDataV3 = new StringBuilder(pubTraceDataBase)
            .append("0A741D02000078BF000000000132F7C9").append(TraceConstants.CONTENT_SPLITOR)
            .append("true").append(TraceConstants.CONTENT_SPLITOR)
            .toString();
        List<TraceContext> traceContextListV3 = MsgTraceDecodeUtil.decoderFromTraceDataString(pubTraceDataV3);
        Assert.assertEquals(1, traceContextListV3.size());
        Assert.assertEquals("Pub", traceContextListV3.get(0).getTraceType().toString());
        Assert.assertTrue(traceContextListV3.get(0).isSuccess());
        Assert.assertEquals("0A741C02622500000000080cc6980189", traceContextListV3.get(0).getTraceBeans().get(0).getMsgId());
        Assert.assertEquals("0A741D02000078BF000000000132F7C9", traceContextListV3.get(0).getTraceBeans().get(0).getOffsetMsgId());
        Assert.assertEquals("10.10.10.10:30911", traceContextListV3.get(0).getTraceBeans().get(0).getStoreHost());
        Assert.assertEquals(traceContextListV3.get(0).getTraceBeans().get(0).getClientHost(), UtilAll.ipToIPv4Str(UtilAll.getIP()));

        String pubTraceDataV4 = new StringBuilder(pubTraceDataBase)
            .append("0A741D02000078BF000000000132F7C9").append(TraceConstants.CONTENT_SPLITOR)
            .append("true").append(TraceConstants.CONTENT_SPLITOR)
            .append("10.10.10.11").append(TraceConstants.CONTENT_SPLITOR)
            .toString();
        List<TraceContext> traceContextListV4 = MsgTraceDecodeUtil.decoderFromTraceDataString(pubTraceDataV4);
        Assert.assertEquals(1, traceContextListV4.size());
        Assert.assertEquals("Pub", traceContextListV4.get(0).getTraceType().toString());
        Assert.assertTrue(traceContextListV4.get(0).isSuccess());
        Assert.assertEquals("0A741C02622500000000080cc6980189", traceContextListV4.get(0).getTraceBeans().get(0).getMsgId());
        Assert.assertEquals("0A741D02000078BF000000000132F7C9", traceContextListV4.get(0).getTraceBeans().get(0).getOffsetMsgId());
        Assert.assertEquals("10.10.10.10:30911", traceContextListV4.get(0).getTraceBeans().get(0).getStoreHost());
        Assert.assertEquals("10.10.10.11", traceContextListV4.get(0).getTraceBeans().get(0).getClientHost());

        String pubTraceDataDefault = new StringBuilder(pubTraceDataBase)
            .append("0A741D02000078BF000000000132F7C9").append(TraceConstants.CONTENT_SPLITOR)
            .append("true").append(TraceConstants.CONTENT_SPLITOR)
            .append("10.10.10.11").append(TraceConstants.CONTENT_SPLITOR)
            .append("10.10.10.11").append(TraceConstants.CONTENT_SPLITOR)
            .toString();
        List<TraceContext> traceContextList = MsgTraceDecodeUtil.decoderFromTraceDataString(pubTraceDataDefault);
        Assert.assertEquals(1, traceContextList.size());
        Assert.assertEquals("Pub", traceContextList.get(0).getTraceType().toString());
        Assert.assertTrue(traceContextList.get(0).isSuccess());
        Assert.assertEquals("0A741C02622500000000080cc6980189", traceContextList.get(0).getTraceBeans().get(0).getMsgId());
        Assert.assertEquals("0A741D02000078BF000000000132F7C9", traceContextList.get(0).getTraceBeans().get(0).getOffsetMsgId());
        Assert.assertEquals("10.10.10.10:30911", traceContextList.get(0).getTraceBeans().get(0).getStoreHost());
        Assert.assertEquals("10.10.10.11", traceContextList.get(0).getTraceBeans().get(0).getClientHost());
    }

    @Test
    public void testDecodeSubTraceMessage() {
        String subTraceDataV1 = new String(subTraceDataBase);
        List<TraceContext> traceContextListV1 = MsgTraceDecodeUtil.decoderFromTraceDataString(subTraceDataV1);
        Assert.assertEquals(2, traceContextListV1.size());
        Assert.assertEquals("SubBefore", traceContextListV1.get(0).getTraceType().toString());
        Assert.assertTrue(traceContextListV1.get(0).isSuccess());
        Assert.assertEquals("0A741C02622500000000080cc698003f", traceContextListV1.get(0).getTraceBeans().get(0).getMsgId());
        Assert.assertEquals(2, traceContextListV1.get(0).getTraceBeans().get(0).getRetryTimes());
        // FIXME bad case for compatibility backward
        // Assert.assertEquals(traceContextListV1.get(0).getTraceBeans().get(0).getClientHost(), "10.10.10.11@39960");
        Assert.assertEquals("SubAfter", traceContextListV1.get(1).getTraceType().toString());
        Assert.assertFalse(traceContextListV1.get(1).isSuccess());
        Assert.assertEquals("0A741C02622500000000080cc698003f", traceContextListV1.get(1).getTraceBeans().get(0).getMsgId());

        String subTraceDataV2 = new StringBuilder(subTraceDataBase)
            .append("4").append(TraceConstants.CONTENT_SPLITOR)
            .toString();
        List<TraceContext> traceContextListV2 = MsgTraceDecodeUtil.decoderFromTraceDataString(subTraceDataV2);
        Assert.assertEquals(2, traceContextListV2.size());
        Assert.assertEquals("SubAfter", traceContextListV2.get(1).getTraceType().toString());
        Assert.assertFalse(traceContextListV2.get(1).isSuccess());
        Assert.assertEquals("0A741C02622500000000080cc698003f", traceContextListV2.get(1).getTraceBeans().get(0).getMsgId());
        Assert.assertEquals(4, traceContextListV2.get(1).getContextCode());

        String subTraceDataV3 = new StringBuilder(subTraceDataBase)
            .append("4").append(TraceConstants.CONTENT_SPLITOR)
            .append("1614666740499").append(TraceConstants.CONTENT_SPLITOR)
            .append("test_consumer_group").append(TraceConstants.CONTENT_SPLITOR)
            .toString();
        List<TraceContext> traceContextListV3 = MsgTraceDecodeUtil.decoderFromTraceDataString(subTraceDataV3);
        Assert.assertEquals(2, traceContextListV3.size());
        Assert.assertEquals("SubAfter", traceContextListV3.get(1).getTraceType().toString());
        Assert.assertFalse(traceContextListV3.get(1).isSuccess());
        Assert.assertEquals("0A741C02622500000000080cc698003f", traceContextListV3.get(1).getTraceBeans().get(0).getMsgId());
        Assert.assertEquals("test_consumer_group", traceContextListV3.get(1).getGroupName());

        String subTraceDataDefault = new StringBuilder(subTraceDataBase)
            .append("4").append(TraceConstants.CONTENT_SPLITOR)
            .append("1614666740499").append(TraceConstants.CONTENT_SPLITOR)
            .append("test_consumer_group").append(TraceConstants.CONTENT_SPLITOR)
            .append("test_consumer_group").append(TraceConstants.CONTENT_SPLITOR)
            .toString();
        List<TraceContext> traceContextList = MsgTraceDecodeUtil.decoderFromTraceDataString(subTraceDataDefault);
        Assert.assertEquals(2, traceContextList.size());
        Assert.assertEquals("SubAfter", traceContextList.get(1).getTraceType().toString());
        Assert.assertFalse(traceContextList.get(1).isSuccess());
        Assert.assertEquals("0A741C02622500000000080cc698003f", traceContextList.get(1).getTraceBeans().get(0).getMsgId());
        Assert.assertEquals("test_consumer_group", traceContextList.get(1).getGroupName());
    }
}
