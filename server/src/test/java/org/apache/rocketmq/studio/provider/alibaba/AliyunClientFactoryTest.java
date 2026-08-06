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
package org.apache.rocketmq.studio.provider.alibaba;

import com.aliyun.sdk.gateway.pop.exception.PopServerException;
import com.aliyun.sdk.service.rocketmq20220801.AsyncClient;
import com.aliyun.sdk.service.rocketmq20220801.models.ListRegionsRequest;
import com.aliyun.sdk.service.rocketmq20220801.models.ListRegionsResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.ListRegionsResponseBody;
import org.apache.rocketmq.studio.cloud.credential.CloudCredentialRepository;
import org.apache.rocketmq.studio.cloud.credential.CloudCredentialVO;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AliyunClientFactoryTest {

    private static final String CREDENTIAL_ID = "cred-1";
    private static final String REGION = "cn-hangzhou";

    @Mock
    private CloudCredentialRepository credentialRepository;

    @Mock
    private AsyncClient asyncClient;

    private AliyunClientFactory factory;

    @BeforeEach
    void setUp() {
        factory = new AliyunClientFactory(credentialRepository);
    }

    @Test
    void endpointShouldFollowRegionTest() {
        assertThat(AliyunClientFactory.endpointFor("cn-hangzhou"))
                .isEqualTo("rocketmq.cn-hangzhou.aliyuncs.com");
        assertThat(AliyunClientFactory.endpointFor("ap-southeast-1"))
                .isEqualTo("rocketmq.ap-southeast-1.aliyuncs.com");
    }

    @Test
    void clientShouldCachePerCredentialAndRegionTest() {
        when(credentialRepository.findById(CREDENTIAL_ID)).thenReturn(Optional.of(credential()));

        AsyncClient first = factory.client(CREDENTIAL_ID, REGION);
        AsyncClient second = factory.client(CREDENTIAL_ID, REGION);
        AsyncClient otherRegion = factory.client(CREDENTIAL_ID, "cn-beijing");

        assertThat(first).isSameAs(second);
        assertThat(otherRegion).isNotSameAs(first);
        factory.close();
    }

    @Test
    void clientShouldThrow404WhenCredentialMissingTest() {
        when(credentialRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> factory.client("missing", REGION))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(404);
    }

    @Test
    void callShouldThrow504OnTimeoutTest() {
        factory.setCallTimeoutSeconds(1L);
        AliyunClientFactory spy = Mockito.spy(factory);
        doReturn(asyncClient).when(spy).client(anyString(), anyString());

        assertThatThrownBy(() -> spy.call(CREDENTIAL_ID, REGION,
                client -> new CompletableFuture<>()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(504);
    }

    @Test
    void callShouldMapServer404ToBusinessExceptionTest() {
        AliyunClientFactory spy = Mockito.spy(factory);
        doReturn(asyncClient).when(spy).client(anyString(), anyString());
        PopServerException error = new PopServerException("instance not found");
        error.setStatusCode(404);
        error.setErrCode("Instance.NotFound");
        when(asyncClient.listRegions(any(ListRegionsRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(error));

        assertThatThrownBy(() -> spy.call(CREDENTIAL_ID, REGION, client -> client.listRegions(
                ListRegionsRequest.builder().build())))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.getCode()).isEqualTo(404);
                    assertThat(business.getMessage()).contains("instance not found");
                });
    }

    @Test
    void callShouldMapInvalidAccessKeyTo401Test() {
        AliyunClientFactory spy = Mockito.spy(factory);
        doReturn(asyncClient).when(spy).client(anyString(), anyString());
        PopServerException error = new PopServerException("bad key");
        error.setStatusCode(403);
        error.setErrCode("InvalidAccessKeyId");
        when(asyncClient.listRegions(any(ListRegionsRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(error));

        assertThatThrownBy(() -> spy.call(CREDENTIAL_ID, REGION, client -> client.listRegions(
                ListRegionsRequest.builder().build())))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(401);
    }

    @Test
    void callShouldMapGenericFailureTo502Test() {
        AliyunClientFactory spy = Mockito.spy(factory);
        doReturn(asyncClient).when(spy).client(anyString(), anyString());
        when(asyncClient.listRegions(any(ListRegionsRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("boom")));

        assertThatThrownBy(() -> spy.call(CREDENTIAL_ID, REGION, client -> client.listRegions(
                ListRegionsRequest.builder().build())))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.getCode()).isEqualTo(502);
                    assertThat(business.getMessage()).contains("boom");
                });
    }

    @Test
    void callShouldReturnSuccessfulBodyTest() {
        AliyunClientFactory spy = Mockito.spy(factory);
        doReturn(asyncClient).when(spy).client(anyString(), anyString());
        ListRegionsResponse response = ListRegionsResponse.create().toBuilder()
                .statusCode(200)
                .body(ListRegionsResponseBody.builder().success(true).build())
                .build();
        when(asyncClient.listRegions(any(ListRegionsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        ListRegionsResponse result = spy.call(CREDENTIAL_ID, REGION,
                client -> client.listRegions(ListRegionsRequest.builder().build()));

        assertThat(result.getBody().getSuccess()).isTrue();
    }

    private CloudCredentialVO credential() {
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setId(CREDENTIAL_ID);
        credential.setName("unit-test");
        credential.setVendor(InstanceVendor.ALIYUN);
        credential.setAccessKey("LTAI5tUnitTestKey000000001");
        credential.setSecretKey("UnitTestSecret000000000000000001");
        return credential;
    }
}
