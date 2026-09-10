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

import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import ResultEmpty from './ResultEmpty';

describe('ResultEmpty', () => {
  it('falls back to the default empty title', () => {
    render(<ResultEmpty />);

    expect(screen.getByText('暂无数据')).toBeTruthy();
  });

  it('renders the custom title and subtitle', () => {
    render(<ResultEmpty title="No clusters" subTitle="Register a cluster to get started" />);

    expect(screen.getByText('No clusters')).toBeTruthy();
    expect(screen.getByText('Register a cluster to get started')).toBeTruthy();
  });
});
