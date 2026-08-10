/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.acl;

import lombok.Builder;
import lombok.Value;
import org.apache.rocketmq.remoting.protocol.body.AclInfo;

import java.util.List;
import java.util.Map;

/** Provider-backed ACL 2.0 read result, retaining per-Broker provenance. */
@Value
@Builder
public class RemoteAclReadResult {
    String source;
    Map<String, List<AclInfo>> policiesByBroker;
    Map<String, String> failuresByBroker;

    public boolean isPartial() {
        return !failuresByBroker.isEmpty() && !policiesByBroker.isEmpty();
    }
}
