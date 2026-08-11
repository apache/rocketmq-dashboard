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

import {buildBrokerTop10Data} from './DashboardPage';

test('uses brokerId in Broker Top 10 labels', () => {
    const brokers = [
        {brokerName: 'broker-a', brokerId: 1, msgGetTotalTodayNow: '20'},
        {brokerName: 'broker-a', brokerId: 0, msgGetTotalTodayNow: '50'},
    ];

    expect(buildBrokerTop10Data(brokers)).toEqual({
        xAxisData: ['broker-a:0', 'broker-a:1'],
        data: [50, 20],
    });
});
