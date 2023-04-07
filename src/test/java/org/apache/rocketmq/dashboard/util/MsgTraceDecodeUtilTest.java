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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MsgTraceDecodeUtilTest {
    private StringBuilder pubTraceDataBase;
    private StringBuilder subTraceDataBase;

    @BeforeEach
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
        Assertions.assertEquals(contexts.size(), 0);
        String pubTraceData_V1 = new String(pubTraceDataBase);
        List<TraceContext> traceContextListV1 = MsgTraceDecodeUtil.decoderFromTraceDataString(pubTraceData_V1);
        Assertions.assertEquals(traceContextListV1.size(), 1);
        Assertions.assertEquals(traceContextListV1.get(0).getTraceType().toString(), "Pub");
        Assertions.assertEquals(traceContextListV1.get(0).isSuccess(), true);
        Assertions.assertEquals(traceContextListV1.get(0).getTraceBeans().get(0).getMsgId(), "0A741C02622500000000080cc6980189");
        Assertions.assertEquals(traceContextListV1.get(0).getTraceBeans().get(0).getOffsetMsgId(), "");
        Assertions.assertEquals(traceContextListV1.get(0).getTraceBeans().get(0).getStoreHost(), "10.10.10.10:30911");
        Assertions.assertEquals(traceContextListV1.get(0).getTraceBeans().get(0).getClientHost(), UtilAll.ipToIPv4Str(UtilAll.getIP()));

        String pubTraceData_V2 = new StringBuilder(pubTraceDataBase)
            .append("false").append(TraceConstants.CONTENT_SPLITOR)
            .toString();
        List<TraceContext> traceContextListV2 = MsgTraceDecodeUtil.decoderFromTraceDataString(pubTraceData_V2);
        Assertions.assertEquals(traceContextListV2.size(), 1);
        Assertions.assertEquals(traceContextListV2.get(0).getTraceType().toString(), "Pub");
        Assertions.assertEquals(traceContextListV2.get(0).isSuccess(), false);
        Assertions.assertEquals(traceContextListV2.get(0).getTraceBeans().get(0).getMsgId(), "0A741C02622500000000080cc6980189");
        Assertions.assertEquals(traceContextListV2.get(0).getTraceBeans().get(0).getOffsetMsgId(), "");
        Assertions.assertEquals(traceContextListV2.get(0).getTraceBeans().get(0).getStoreHost(), "10.10.10.10:30911");
        Assertions.assertEquals(traceContextListV2.get(0).getTraceBeans().get(0).getClientHost(), UtilAll.ipToIPv4Str(UtilAll.getIP()));

        String pubTraceData_V3 = new StringBuilder(pubTraceDataBase)
            .append("0A741D02000078BF000000000132F7C9").append(TraceConstants.CONTENT_SPLITOR)
            .append("true").append(TraceConstants.CONTENT_SPLITOR)
            .toString();
        List<TraceContext> traceContextListV3 = MsgTraceDecodeUtil.decoderFromTraceDataString(pubTraceData_V3);
        Assertions.assertEquals(traceContextListV3.size(), 1);
        Assertions.assertEquals(traceContextListV3.get(0).getTraceType().toString(), "Pub");
        Assertions.assertEquals(traceContextListV3.get(0).isSuccess(), true);
        Assertions.assertEquals(traceContextListV3.get(0).getTraceBeans().get(0).getMsgId(), "0A741C02622500000000080cc6980189");
        Assertions.assertEquals(traceContextListV3.get(0).getTraceBeans().get(0).getOffsetMsgId(), "0A741D02000078BF000000000132F7C9");
        Assertions.assertEquals(traceContextListV3.get(0).getTraceBeans().get(0).getStoreHost(), "10.10.10.10:30911");
        Assertions.assertEquals(traceContextListV3.get(0).getTraceBeans().get(0).getClientHost(), UtilAll.ipToIPv4Str(UtilAll.getIP()));

        String pubTraceData_V4 = new StringBuilder(pubTraceDataBase)
            .append("0A741D02000078BF000000000132F7C9").append(TraceConstants.CONTENT_SPLITOR)
            .append("true").append(TraceConstants.CONTENT_SPLITOR)
            .append("10.10.10.11").append(TraceConstants.CONTENT_SPLITOR)
            .toString();
        List<TraceContext> traceContextListV4 = MsgTraceDecodeUtil.decoderFromTraceDataString(pubTraceData_V4);
        Assertions.assertEquals(traceContextListV4.size(), 1);
        Assertions.assertEquals(traceContextListV4.get(0).getTraceType().toString(), "Pub");
        Assertions.assertEquals(traceContextListV4.get(0).isSuccess(), true);
        Assertions.assertEquals(traceContextListV4.get(0).getTraceBeans().get(0).getMsgId(), "0A741C02622500000000080cc6980189");
        Assertions.assertEquals(traceContextListV4.get(0).getTraceBeans().get(0).getOffsetMsgId(), "0A741D02000078BF000000000132F7C9");
        Assertions.assertEquals(traceContextListV4.get(0).getTraceBeans().get(0).getStoreHost(), "10.10.10.10:30911");
        Assertions.assertEquals(traceContextListV4.get(0).getTraceBeans().get(0).getClientHost(), "10.10.10.11");

        String pubTraceData_default = new StringBuilder(pubTraceDataBase)
            .append("0A741D02000078BF000000000132F7C9").append(TraceConstants.CONTENT_SPLITOR)
            .append("true").append(TraceConstants.CONTENT_SPLITOR)
            .append("10.10.10.11").append(TraceConstants.CONTENT_SPLITOR)
            .append("10.10.10.11").append(TraceConstants.CONTENT_SPLITOR)
            .toString();
        List<TraceContext> traceContextList = MsgTraceDecodeUtil.decoderFromTraceDataString(pubTraceData_default);
        Assertions.assertEquals(traceContextList.size(), 1);
        Assertions.assertEquals(traceContextList.get(0).getTraceType().toString(), "Pub");
        Assertions.assertEquals(traceContextList.get(0).isSuccess(), true);
        Assertions.assertEquals(traceContextList.get(0).getTraceBeans().get(0).getMsgId(), "0A741C02622500000000080cc6980189");
        Assertions.assertEquals(traceContextList.get(0).getTraceBeans().get(0).getOffsetMsgId(), "0A741D02000078BF000000000132F7C9");
        Assertions.assertEquals(traceContextList.get(0).getTraceBeans().get(0).getStoreHost(), "10.10.10.10:30911");
        Assertions.assertEquals(traceContextList.get(0).getTraceBeans().get(0).getClientHost(), "10.10.10.11");
    }

    @Test
    public void testDecodeSubTraceMessage() {
        String subTraceData_V1 = new String(subTraceDataBase);
        List<TraceContext> traceContextListV1 = MsgTraceDecodeUtil.decoderFromTraceDataString(subTraceData_V1);
        Assertions.assertEquals(traceContextListV1.size(), 2);
        Assertions.assertEquals(traceContextListV1.get(0).getTraceType().toString(), "SubBefore");
        Assertions.assertEquals(traceContextListV1.get(0).isSuccess(), true);
        Assertions.assertEquals(traceContextListV1.get(0).getTraceBeans().get(0).getMsgId(), "0A741C02622500000000080cc698003f");
        Assertions.assertEquals(traceContextListV1.get(0).getTraceBeans().get(0).getRetryTimes(), 2);
        // FIXME bad case for compatibility backward
        // Assertions.assertEquals(traceContextListV1.get(0).getTraceBeans().get(0).getClientHost(), "10.10.10.11@39960");
        Assertions.assertEquals(traceContextListV1.get(1).getTraceType().toString(), "SubAfter");
        Assertions.assertEquals(traceContextListV1.get(1).isSuccess(), false);
        Assertions.assertEquals(traceContextListV1.get(1).getTraceBeans().get(0).getMsgId(), "0A741C02622500000000080cc698003f");

        String subTraceData_V2 = new StringBuilder(subTraceDataBase)
            .append("4").append(TraceConstants.CONTENT_SPLITOR)
            .toString();
        List<TraceContext> traceContextListV2 = MsgTraceDecodeUtil.decoderFromTraceDataString(subTraceData_V2);
        Assertions.assertEquals(traceContextListV2.size(), 2);
        Assertions.assertEquals(traceContextListV2.get(1).getTraceType().toString(), "SubAfter");
        Assertions.assertEquals(traceContextListV2.get(1).isSuccess(), false);
        Assertions.assertEquals(traceContextListV2.get(1).getTraceBeans().get(0).getMsgId(), "0A741C02622500000000080cc698003f");
        Assertions.assertEquals(traceContextListV2.get(1).getContextCode(), 4);

        String subTraceData_V3 = new StringBuilder(subTraceDataBase)
            .append("4").append(TraceConstants.CONTENT_SPLITOR)
            .append("1614666740499").append(TraceConstants.CONTENT_SPLITOR)
            .append("test_consumer_group").append(TraceConstants.CONTENT_SPLITOR)
            .toString();
        List<TraceContext> traceContextListV3 = MsgTraceDecodeUtil.decoderFromTraceDataString(subTraceData_V3);
        Assertions.assertEquals(traceContextListV3.size(), 2);
        Assertions.assertEquals(traceContextListV3.get(1).getTraceType().toString(), "SubAfter");
        Assertions.assertEquals(traceContextListV3.get(1).isSuccess(), false);
        Assertions.assertEquals(traceContextListV3.get(1).getTraceBeans().get(0).getMsgId(), "0A741C02622500000000080cc698003f");
        Assertions.assertEquals(traceContextListV3.get(1).getGroupName(), "test_consumer_group");

        String subTraceData_default = new StringBuilder(subTraceDataBase)
            .append("4").append(TraceConstants.CONTENT_SPLITOR)
            .append("1614666740499").append(TraceConstants.CONTENT_SPLITOR)
            .append("test_consumer_group").append(TraceConstants.CONTENT_SPLITOR)
            .append("test_consumer_group").append(TraceConstants.CONTENT_SPLITOR)
            .toString();
        List<TraceContext> traceContextList = MsgTraceDecodeUtil.decoderFromTraceDataString(subTraceData_default);
        Assertions.assertEquals(traceContextList.size(), 2);
        Assertions.assertEquals(traceContextList.get(1).getTraceType().toString(), "SubAfter");
        Assertions.assertEquals(traceContextList.get(1).isSuccess(), false);
        Assertions.assertEquals(traceContextList.get(1).getTraceBeans().get(0).getMsgId(), "0A741C02622500000000080cc698003f");
        Assertions.assertEquals(traceContextList.get(1).getGroupName(), "test_consumer_group");
    }
}
