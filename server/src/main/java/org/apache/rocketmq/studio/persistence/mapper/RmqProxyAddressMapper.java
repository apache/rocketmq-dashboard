/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.rocketmq.studio.persistence.entity.RmqProxyAddress;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RmqProxyAddressMapper extends BaseMapper<RmqProxyAddress> {
}
