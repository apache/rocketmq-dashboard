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

import React from 'react';
import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import dayjs from 'dayjs';
import MessageQueryPage from './message';
import {LanguageProvider} from '../../i18n/LanguageContext';
import {remoteApi} from '../../api/remoteApi/remoteApi';

jest.mock('../../api/remoteApi/remoteApi', () => ({
    remoteApi: {
        queryTopic: jest.fn(),
        queryMessagePageByTopic: jest.fn(),
    },
}));

// jsdom does not implement matchMedia. Rendering this page mounts an antd Table, whose responsive
// observer calls it on mount, so the test stubs it instead of touching setupTests.js.
if (!window.matchMedia) {
    window.matchMedia = (query) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => {
        },
        removeListener: () => {
        },
        addEventListener: () => {
        },
        removeEventListener: () => {
        },
        dispatchEvent: () => false,
    });
}

const TIME_FORMAT = 'YYYY-MM-DD HH:mm:ss';

const formatTimestamp = (timestamp) => dayjs(timestamp).format(TIME_FORMAT);

describe('Message page DatePicker state', () => {
    // react-scripts runs jest with resetMocks, so the implementations belong here and not in the
    // module factory above.
    beforeEach(() => {
        remoteApi.queryTopic.mockResolvedValue({status: 0, data: {topicList: ['TestTopic']}});
        remoteApi.queryMessagePageByTopic.mockResolvedValue({
            status: 0,
            data: {page: {content: [], number: 0, totalElements: 0}, taskId: 'task-1'},
        });
    });

    const renderPage = () => render(
        <LanguageProvider>
            <MessageQueryPage/>
        </LanguageProvider>
    );

    // The pickers hold their bound value by reference. antd v5's DatePicker is dayjs-backed and its
    // generateConfig helpers never clone (setDate -> date.date(num) and friends), which is safe for
    // dayjs but mutates a Moment in place. If the page ever hands Moment values to these pickers
    // again, merely opening the panel rewrites the page state, so the displayed time and the time
    // that is actually queried drift apart.
    // 30s budget: rendering the whole page (antd Table plus pickers) is slow enough to brush
    // against the default 5s when the suite runs alongside other files.
    test('hovering an open panel keeps the picked times unchanged and queried as displayed', async () => {
        renderPage();

        await waitFor(() => expect(remoteApi.queryTopic).toHaveBeenCalled());

        const pickerInputs = screen.getAllByRole('textbox');
        expect(pickerInputs.length).toBeGreaterThanOrEqual(2);
        const beginInput = pickerInputs[0];
        const endInput = pickerInputs[1];

        const beginDisplayed = beginInput.value;
        const endDisplayed = endInput.value;
        expect(beginDisplayed).not.toBe('');
        expect(endDisplayed).not.toBe('');

        // Open the begin picker and sweep the mouse across its cells, the way the reported
        // reproduction does.
        fireEvent.focus(beginInput);
        fireEvent.click(beginInput);

        const cells = await screen.findAllByText(/^\d{1,2}$/);
        expect(cells.length).toBeGreaterThan(0);
        cells.forEach((cell) => fireEvent.mouseEnter(cell));
        fireEvent.mouseOver(cells[cells.length - 1]);

        // Closing the panel drops the hover preview and must fall back to the value the page holds,
        // which is what the reported reproduction says Esc fails to do.
        fireEvent.keyDown(beginInput, {key: 'Escape', code: 'Escape'});
        await waitFor(() => expect(beginInput.value).toBe(beginDisplayed));
        await waitFor(() => expect(endInput.value).toBe(endDisplayed));

        // And the query must carry exactly the times the user sees.
        fireEvent.mouseDown(screen.getByRole('combobox'));
        fireEvent.click(await screen.findByTitle('TestTopic'));
        fireEvent.click(screen.getByRole('button', {name: /search/i}));

        await waitFor(() => expect(remoteApi.queryMessagePageByTopic).toHaveBeenCalled());
        const [, beginQueried, endQueried] = remoteApi.queryMessagePageByTopic.mock.calls[0];
        expect(formatTimestamp(beginQueried)).toBe(beginDisplayed);
        expect(formatTimestamp(endQueried)).toBe(endDisplayed);
    }, 30000);
});
