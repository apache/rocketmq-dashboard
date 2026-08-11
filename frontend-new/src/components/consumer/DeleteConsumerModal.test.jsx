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

import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {remoteApi} from '../../api/remoteApi/remoteApi';
import DeleteConsumerModal from './DeleteConsumerModal';

jest.mock('antd', () => {
    const React = require('react');
    const GroupContext = React.createContext(null);
    const Checkbox = ({value, children}) => {
        const group = React.useContext(GroupContext);
        const checked = group.value.includes(value);
        return (
            <label>
                <input
                    type="checkbox"
                    checked={checked}
                    onChange={(event) => {
                        const nextValue = event.target.checked
                            ? [...group.value, value]
                            : group.value.filter(item => item !== value);
                        group.onChange(nextValue);
                    }}
                />
                {children}
            </label>
        );
    };
    Checkbox.Group = ({value, onChange, children}) => (
        <GroupContext.Provider value={{value, onChange}}>{children}</GroupContext.Provider>
    );

    return {
        Button: ({children, onClick}) => <button onClick={onClick}>{children}</button>,
        Checkbox,
        Modal: ({visible, title, footer, children}) => visible && <div>{title}{children}{footer}</div>,
        notification: {success: jest.fn(), warning: jest.fn()},
        Spin: ({children}) => children,
    };
});

jest.mock('../../api/remoteApi/remoteApi', () => ({
    remoteApi: {
        deleteConsumerGroup: jest.fn(),
        fetchBrokerNameList: jest.fn(),
    },
}));

const t = {
    CANCEL: 'Cancel',
    CONFIRM_DELETE: 'Confirm delete',
    DELETE_CONSUMER_GROUP: 'Delete consumer group',
    SELECT_DELETE_BROKERS: 'Select brokers',
};

test('clears old brokers and selections when reopened for another group', async () => {
    remoteApi.fetchBrokerNameList
        .mockResolvedValueOnce({status: 0, data: ['broker-a']})
        .mockResolvedValueOnce({status: 0, data: ['broker-b']});

    const commonProps = {
        onCancel: jest.fn(),
        onSuccess: jest.fn(),
        t,
    };
    const {rerender} = render(
        <DeleteConsumerModal {...commonProps} visible group="group-a"/>
    );

    const brokerA = await screen.findByRole('checkbox', {name: 'broker-a'});
    userEvent.click(brokerA);
    await waitFor(() => expect(brokerA).toBeChecked());

    rerender(<DeleteConsumerModal {...commonProps} visible={false} group="group-a"/>);
    rerender(<DeleteConsumerModal {...commonProps} visible group="group-b"/>);

    await waitFor(() => expect(screen.queryByText('broker-a')).not.toBeInTheDocument());
    const brokerB = await screen.findByRole('checkbox', {name: 'broker-b'});
    expect(brokerB).not.toBeChecked();
});
