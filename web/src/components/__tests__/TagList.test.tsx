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

import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import TagList from '../TagList';

const items = [
  { key: 'dingtalk', label: 'DingTalk', color: 'blue' },
  { key: 'email', label: 'Email', color: 'green' },
  { key: 'sms', label: 'SMS', color: 'orange' },
];

describe('TagList', () => {
  it('renders every item when no max is givenTest', () => {
    render(<TagList items={items} />);
    expect(screen.getByText('DingTalk')).toBeInTheDocument();
    expect(screen.getByText('Email')).toBeInTheDocument();
    expect(screen.getByText('SMS')).toBeInTheDocument();
    expect(screen.queryByText('+1')).not.toBeInTheDocument();
  });

  it('renders nothing when there are no itemsTest', () => {
    const { container } = render(<TagList items={[]} />);
    expect(container.querySelectorAll('.ant-tag')).toHaveLength(0);
  });

  it('cancels the antd tag margin so the container gap is the only spacingTest', () => {
    render(<TagList items={items} />);
    // antd's own `.ant-tag` carries margin-inline-end: 8px, which would stack on top of gap={4}
    // and render as 12px of spacing. Every tag has to cancel it.
    screen
      .getAllByText(/DingTalk|Email|SMS/)
      .forEach((tag) => expect(tag.style.marginInlineEnd).toBe('0px'));
  });

  it('never wraps onto a second lineTest', () => {
    const { container } = render(<TagList items={items} />);
    expect(container.querySelector('.ant-flex-wrap-wrap')).toBeNull();
  });

  it('collapses the overflow into a +N tag that lists the hidden labelsTest', async () => {
    render(<TagList items={items} max={1} />);
    expect(screen.getByText('DingTalk')).toBeInTheDocument();
    expect(screen.queryByText('Email')).not.toBeInTheDocument();

    fireEvent.mouseEnter(screen.getByText('+2'));
    expect(await screen.findByText('Email')).toBeInTheDocument();
    expect(screen.getByText('SMS')).toBeInTheDocument();
  });

  it('exposes hidden labels on keyboard focus', async () => {
    render(<TagList items={items} max={1} />);
    const overflow = screen.getByText('+2');

    expect(overflow).toHaveAttribute('tabindex', '0');
    expect(overflow).toHaveAttribute('aria-label', '+2: Email, SMS');
    fireEvent.focus(overflow);

    expect(await screen.findByText('Email')).toBeInTheDocument();
    expect(screen.getByText('SMS')).toBeInTheDocument();
  });

  it('keeps every item when max is above the item countTest', () => {
    render(<TagList items={items} max={5} />);
    expect(screen.getByText('SMS')).toBeInTheDocument();
    expect(screen.queryByText(/\+\d/)).not.toBeInTheDocument();
  });

  it('truncates long labels but keeps them reachable through the titleTest', () => {
    render(<TagList items={[{ key: 't', label: 'a-very-long-topic-name' }]} tagMaxWidth={80} />);
    const tag = screen.getByText('a-very-long-topic-name');
    expect(tag.style.maxWidth).toBe('80px');
    expect(tag.style.textOverflow).toBe('ellipsis');
    expect(tag.getAttribute('title')).toBe('a-very-long-topic-name');
  });

  it('does not add a title when labels are not truncatedTest', () => {
    render(<TagList items={[{ key: 't', label: 'SMS' }]} />);
    expect(screen.getByText('SMS').getAttribute('title')).toBeNull();
  });
});
