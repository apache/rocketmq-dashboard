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

import {validateMessageTimeRange} from './messageQueryValidation';

describe('validateMessageTimeRange', () => {
    test('rejects a cleared date without reading the other date value', () => {
        const end = {valueOf: jest.fn()};

        expect(validateMessageTimeRange(null, end)).toBe('required');
        expect(end.valueOf).not.toHaveBeenCalled();
    });

    test('rejects an end time earlier than the begin time', () => {
        expect(validateMessageTimeRange({valueOf: () => 2}, {valueOf: () => 1})).toBe('order');
    });

    test('accepts a complete ordered range', () => {
        expect(validateMessageTimeRange({valueOf: () => 1}, {valueOf: () => 2})).toBeNull();
    });
});
