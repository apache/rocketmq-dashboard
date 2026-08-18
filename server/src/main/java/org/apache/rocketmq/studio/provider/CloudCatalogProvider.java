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
package org.apache.rocketmq.studio.provider;

import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;

import java.util.List;

/**
 * Cloud catalog discovery SPI: list regions / cloud instances with a stored credential.
 * Commercial instances are never created manually; users pick one from this catalog.
 */
public interface CloudCatalogProvider {

    InstanceVendor vendor();

    List<CloudRegionVO> listRegions(Long credentialId);

    List<CloudInstanceOptionVO> listCloudInstances(Long credentialId, String regionId, String search);

    CloudInstanceDetailVO getCloudInstance(Long credentialId, String regionId, String cloudInstanceId);
}
