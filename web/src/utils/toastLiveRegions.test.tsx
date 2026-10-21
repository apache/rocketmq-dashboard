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
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp, Button, message, notification } from 'antd';
import {
  getMessageLiveRegion,
  getNotificationLiveRegion,
} from './toastLiveRegions';

const fireAppContextToast = () => {
  const ToastPage = () => {
    const { message: appMessage, notification: appNotification } = AntdApp.useApp();
    return (
      <>
        <Button onClick={() => appMessage.success('app-context toast done')}>fire message</Button>
        <Button onClick={() => appNotification.open({ message: 'app-context notice done' })}>
          fire notification
        </Button>
      </>
    );
  };
  return (
    <AntdApp
      message={{ getContainer: getMessageLiveRegion }}
      notification={{ getContainer: getNotificationLiveRegion }}
    >
      <ToastPage />
    </AntdApp>
  );
};

const textOf = (element: HTMLElement | null) => (element?.textContent ?? '').trim();

describe('toast live regions', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
  });

  afterEach(() => {
    message.destroy();
    notification.destroy();
  });

  it('creates exactly one polite status region per toast kindTest', () => {
    const messageRegion = getMessageLiveRegion();
    const notificationRegion = getNotificationLiveRegion();

    expect(messageRegion.getAttribute('role')).toBe('status');
    expect(messageRegion.getAttribute('aria-live')).toBe('polite');
    expect(messageRegion).toBe(getMessageLiveRegion());
    expect(notificationRegion.getAttribute('role')).toBe('status');
    expect(notificationRegion.getAttribute('aria-live')).toBe('polite');
    expect(notificationRegion).toBe(getNotificationLiveRegion());
    expect(messageRegion).not.toBe(notificationRegion);
    expect(document.body.contains(messageRegion)).toBe(true);
    expect(document.body.contains(notificationRegion)).toBe(true);
  });

  it('announces static singleton toasts through the live regionTest', async () => {
    message.config({ getContainer: getMessageLiveRegion });
    notification.config({ getContainer: getNotificationLiveRegion });

    message.success('static toast done');
    notification.open({ message: 'static notice done' });

    const messageRegion = getMessageLiveRegion();
    const notificationRegion = getNotificationLiveRegion();
    await waitFor(() => expect(textOf(messageRegion)).toContain('static toast done'));
    await waitFor(() => expect(textOf(notificationRegion)).toContain('static notice done'));
  });

  it('announces App.useApp toasts through the live regionTest', async () => {
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    render(fireAppContextToast());

    await user.click(screen.getByRole('button', { name: 'fire message' }));
    await user.click(screen.getByRole('button', { name: 'fire notification' }));

    await waitFor(() => expect(textOf(getMessageLiveRegion())).toContain('app-context toast done'));
    await waitFor(() =>
      expect(textOf(getNotificationLiveRegion())).toContain('app-context notice done'),
    );
  });
});
