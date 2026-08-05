/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.rocketmq.studio.instance.message;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MessageServiceTest {

    @Test
    void rejectsReversedTopicQueryWindowBeforeCallingProvider() {
        MessageProvider provider = mock(MessageProvider.class);
        MessageService service = new MessageService(provider);

        assertThatThrownBy(() -> service.queryMessages("TopicA", null, null, null, 200L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("startTime must not be after endTime");

        verifyNoInteractions(provider);
    }

    @Test
    void rejectsTopicQueryWindowLongerThanSevenDaysBeforeCallingProvider() {
        MessageProvider provider = mock(MessageProvider.class);
        MessageService service = new MessageService(provider);

        assertThatThrownBy(() -> service.queryMessages("TopicA", null, null, null, 0L,
                8L * 24 * 60 * 60 * 1000))
                .isInstanceOf(BusinessException.class)
                .hasMessage("topic query time range must not exceed 7 days");

        verifyNoInteractions(provider);
    }
}
