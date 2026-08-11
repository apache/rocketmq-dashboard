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

import {render, waitFor} from '@testing-library/react';
import {remoteApi} from '../../api/remoteApi/remoteApi';
import Acl from './acl';

jest.mock('antd', () => {
    const React = require('react');
    const Component = ({children}) => <div>{children}</div>;
    const Form = Component;
    Form.Item = Component;
    Form.useForm = () => [{resetFields: jest.fn(), setFieldsValue: jest.fn(), validateFields: jest.fn()}];
    const Input = Component;
    Input.Password = Component;
    Input.Search = Component;
    const Select = Component;
    Select.Option = Component;
    const Tabs = Component;
    Tabs.TabPane = Component;
    return {
        Button: Component,
        Form,
        Input,
        message: {useMessage: () => [{error: jest.fn(), success: jest.fn()}, null]},
        Modal: Component,
        Popconfirm: Component,
        Select,
        Space: Component,
        Table: Component,
        Tabs,
        Tag: Component,
    };
});

jest.mock('@ant-design/icons', () => ({
    DeleteOutlined: () => null,
    EditOutlined: () => null,
    EyeInvisibleOutlined: () => null,
    EyeOutlined: () => null,
}));

jest.mock('../../api/remoteApi/remoteApi', () => ({
    remoteApi: {
        getClusterList: jest.fn(),
        listUsers: jest.fn(),
        listAcls: jest.fn(),
    },
}));

jest.mock('../../components/acl/ResourceInput', () => () => null);
jest.mock('../../components/acl/SubjectInput', () => () => null);

describe('Acl initial data loading', () => {
    test('loads users after selecting the first broker asynchronously', async () => {
        remoteApi.getClusterList.mockResolvedValue({
            status: 0,
            data: {
                clusterInfo: {
                    clusterAddrTable: {'cluster-a': ['broker-a']},
                    brokerAddrTable: {
                        'broker-a': {brokerAddrs: {'0': '127.0.0.1:10911'}},
                    },
                },
            },
        });
        remoteApi.listUsers.mockResolvedValue({status: 0, data: []});

        render(<Acl/>);

        await waitFor(() => {
            expect(remoteApi.listUsers).toHaveBeenCalledWith('broker-a', 'cluster-a');
        });
    });
});
