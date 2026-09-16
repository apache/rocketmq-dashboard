/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.message;

import java.util.List;

public record ConsumeQueueSnapshot(String brokerAddress, String minIndex, String maxIndex,
        String requestedIndex, int count, boolean atEnd, String expressionType, String expression,
        String filterData, List<Entry> entries) {
    public record Entry(int ordinal, String physicalOffset, int physicalSize, String tagsCode,
            String extension, String bitmap, Boolean indexMatch, String message) {
    }
}
