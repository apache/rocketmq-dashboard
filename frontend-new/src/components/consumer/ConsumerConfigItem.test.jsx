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

import {remoteApi} from '../../api/remoteApi/remoteApi';
import {submitConsumerConfig} from './ConsumerConfigItem';

jest.mock('../../api/remoteApi/remoteApi', () => ({
    remoteApi: {
        createOrUpdateConsumer: jest.fn(),
    },
}));

const values = {
    groupName: 'group-a',
    brokerName: ['broker-a'],
    clusterName: ['cluster-a'],
    retryQueueNums: 1,
    retryMaxTimes: 16,
    brokerId: 0,
    whichBrokerWhenConsumeSlowly: 0,
};

const createArguments = (validateFields = jest.fn().mockResolvedValue(values)) => ({
    form: {validateFields},
    initialConfig: {subscriptionGroupConfig: {}},
    isAddConfig: true,
    group: 'group-a',
    onCancel: jest.fn(),
    onSuccess: jest.fn(),
    t: {
        FORM_VALIDATION_FAILED: 'Validation failed',
        OPERATION_FAILED: 'Operation failed',
        SUCCESS: 'Success',
    },
    messageApi: {
        error: jest.fn(),
        success: jest.fn(),
    },
});

beforeEach(() => {
    jest.clearAllMocks();
    jest.spyOn(console, 'error').mockImplementation(() => {});
});

afterEach(() => {
    console.error.mockRestore();
});

test('keeps the form open when validation fails', async () => {
    const args = createArguments(jest.fn().mockRejectedValue(new Error('invalid form')));

    await submitConsumerConfig(args);

    expect(remoteApi.createOrUpdateConsumer).not.toHaveBeenCalled();
    expect(args.onSuccess).not.toHaveBeenCalled();
    expect(args.onCancel).not.toHaveBeenCalled();
    expect(args.messageApi.error).toHaveBeenCalledWith(args.t.FORM_VALIDATION_FAILED);
});

test('keeps the form open when the API rejects the update', async () => {
    remoteApi.createOrUpdateConsumer.mockResolvedValue({status: 1, errMsg: 'rejected'});
    const args = createArguments();

    await submitConsumerConfig(args);

    expect(args.onSuccess).not.toHaveBeenCalled();
    expect(args.onCancel).not.toHaveBeenCalled();
    expect(args.messageApi.error).toHaveBeenCalledWith('Operation failed: rejected');
});

test('closes the form only after a successful update', async () => {
    remoteApi.createOrUpdateConsumer.mockResolvedValue({status: 0});
    const args = createArguments();

    await submitConsumerConfig(args);

    expect(args.messageApi.success).toHaveBeenCalledWith(args.t.SUCCESS);
    expect(args.onSuccess).toHaveBeenCalledTimes(1);
    expect(args.onCancel).toHaveBeenCalledTimes(1);
});
