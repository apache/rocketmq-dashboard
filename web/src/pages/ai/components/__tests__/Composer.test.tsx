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

import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { App } from 'antd';
import { LangProvider } from '../../../../i18n/LangContext';
import type { LlmConfig } from '../../../../api/llm';
import Composer, { type ComposerProps } from '../Composer';

/**
 * The composer's two pieces of logic worth pinning: the send/stop state derivation and the keyboard
 * contract.
 *
 * The derivation is three lines and gets every edge wrong if it is written from memory. `stopping` in
 * particular is not cosmetic: a stop is a server round-trip and the stream stays open to deliver the
 * terminal frames, so without that state the button would flip straight back to `send` and invite a
 * second click on a run that is already dying.
 *
 * The keyboard contract carries one non-obvious rule — the `isComposing` guard. Confirming a CJK
 * candidate with Enter fires a keydown whose `key` is `Enter`; without the guard, typing Chinese
 * sends half a sentence.
 */

const READY_CONFIG: LlmConfig = {
  provider: 'openai',
  apiBase: 'https://api.openai.com/v1',
  model: 'gpt-4o',
  maxTokens: 1024,
  temperature: 0.2,
  enabled: true,
  ready: true,
};

beforeAll(() => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
});

function baseProps(overrides: Partial<ComposerProps> = {}): ComposerProps {
  return {
    value: '',
    onChange: vi.fn(),
    onSend: vi.fn(),
    onStop: vi.fn(),
    isStreaming: false,
    stopRequested: false,
    llmReady: true,
    llmConfig: READY_CONFIG,
    model: 'gpt-4o',
    modelOptions: [{ value: 'gpt-4o', label: 'gpt-4o' }],
    onModelChange: vi.fn(),
    engine: 'claude-code',
    onEngineChange: vi.fn(),
    mode: 'chat',
    onModeChange: vi.fn(),
    enhance: false,
    onEnhanceChange: vi.fn(),
    onOpenTools: vi.fn(),
    onOpenPromptTemplates: vi.fn(),
    onOpenHistory: vi.fn(),
    ...overrides,
  };
}

function renderComposer(overrides: Partial<ComposerProps> = {}) {
  const props = baseProps(overrides);
  const view = render(
    <App>
      <LangProvider>
        <MemoryRouter>
          <Composer {...props} />
        </MemoryRouter>
      </LangProvider>
    </App>,
  );
  /** Re-render over the SAME handlers, so call counts accumulate across the change. */
  const rerenderWith = (next: Partial<ComposerProps>) =>
    view.rerender(
      <App>
        <LangProvider>
          <MemoryRouter>
            <Composer {...{ ...props, ...next }} />
          </MemoryRouter>
        </LangProvider>
      </App>,
    );
  return { ...view, props, rerenderWith };
}

const textarea = () => screen.getByRole('textbox');
const sendStop = () => screen.getByTestId('ai-send-stop-button');

describe('Composer', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('offersSendOnlyWhenThereIsSomethingToSendTest', () => {
    const { rerenderWith } = renderComposer({ value: '   ' });
    expect(sendStop()).toHaveAttribute('data-state', 'send');
    expect(sendStop()).toBeDisabled();

    rerenderWith({ value: '检查集群状态' });
    expect(sendStop()).toBeEnabled();
  });

  it('keepsSendDisabledWhileTheProviderIsNotReadyTest', () => {
    renderComposer({ value: '检查集群状态', llmReady: false });
    expect(sendStop()).toHaveAttribute('data-state', 'send');
    expect(sendStop()).toBeDisabled();
  });

  it('turnsTheSameSlotIntoStopWhileARunIsGeneratingTest', async () => {
    const user = userEvent.setup();
    const { props } = renderComposer({ value: '检查集群状态', isStreaming: true });

    // Stop is reachable with an empty draft too: `canSend` describes the send state only.
    expect(sendStop()).toHaveAttribute('data-state', 'stop');
    expect(sendStop()).toBeEnabled();
    expect(screen.getAllByRole('button', { name: /Stop|停止/ })).toHaveLength(1);

    await user.click(sendStop());
    expect(props.onStop).toHaveBeenCalledTimes(1);
    expect(props.onSend).not.toHaveBeenCalled();
  });

  it('showsStoppingUntilTheTerminalFramesArriveTest', async () => {
    const user = userEvent.setup();
    const { props } = renderComposer({
      value: '检查集群状态',
      isStreaming: true,
      stopRequested: true,
    });

    expect(sendStop()).toHaveAttribute('data-state', 'stopping');
    expect(sendStop()).toBeDisabled();
    await user.click(sendStop());
    expect(props.onStop).not.toHaveBeenCalled();
    expect(props.onSend).not.toHaveBeenCalled();
  });

  it('sendsOnEnterAndClearsTheDraftTest', () => {
    const { props } = renderComposer({ value: '检查集群状态' });

    fireEvent.keyDown(textarea(), { key: 'Enter' });

    expect(props.onSend).toHaveBeenCalledWith('检查集群状态');
    // Clearing is the composer's job: a caller that has to reject the send puts the text back.
    expect(props.onChange).toHaveBeenCalledWith('');
  });

  it('keepsShiftEnterForANewlineTest', () => {
    const { props } = renderComposer({ value: '第一行' });

    fireEvent.keyDown(textarea(), { key: 'Enter', shiftKey: true });

    expect(props.onSend).not.toHaveBeenCalled();
  });

  it('doesNotSendWhileAnImeCompositionIsBeingConfirmedTest', () => {
    const { props } = renderComposer({ value: '检查集群状态' });

    fireEvent.keyDown(textarea(), { key: 'Enter', isComposing: true });

    expect(props.onSend).not.toHaveBeenCalled();
  });

  it('stopsOnEscapeWhileGeneratingTest', () => {
    const { props } = renderComposer({ value: '检查集群状态', isStreaming: true });

    fireEvent.keyDown(textarea(), { key: 'Escape' });

    expect(props.onStop).toHaveBeenCalledTimes(1);
  });

  it('ignoresEscapeWhenNothingIsGeneratingTest', () => {
    const { props } = renderComposer({ value: '检查集群状态' });

    fireEvent.keyDown(textarea(), { key: 'Escape' });

    expect(props.onStop).not.toHaveBeenCalled();
    expect(props.onSend).not.toHaveBeenCalled();
  });

  it('disablesTheInputEntirelyWhenThePageIsDisabledTest', () => {
    renderComposer({ value: '检查集群状态', disabled: true });

    expect(textarea()).toBeDisabled();
    expect(sendStop()).toBeDisabled();
  });

  it('fillsTheDraftFromAQuickActionInsteadOfSendingItTest', async () => {
    const user = userEvent.setup();
    const { props } = renderComposer();

    await user.click(screen.getByText('查看集群状态'));

    expect(props.onChange).toHaveBeenCalledWith('查看集群状态');
    expect(props.onSend).not.toHaveBeenCalled();
  });

  it('explainsAMissingProviderWithANeutralBannerTest', () => {
    const { container } = renderComposer({
      llmReady: false,
      llmConfig: { ...READY_CONFIG, enabled: false, ready: false },
    });

    // Persistent explanatory notices use the neutral InfoBanner treatment, never a coloured Alert.
    expect(screen.getByTestId('ai-not-ready-banner')).toBeInTheDocument();
    expect(container.querySelector('.ant-alert')).toBeNull();
    expect(screen.getByRole('button', { name: '去配置' })).toBeInTheDocument();
  });

  it('showsNoNoticeAtAllWhenTheProviderIsReadyTest', () => {
    const { container } = renderComposer();

    // The always-on settings hint was removed: once the provider works, pointing at the settings
    // page on every visit is noise. Only conditional, actionable notices may render.
    expect(screen.queryByTestId('ai-settings-hint-banner')).not.toBeInTheDocument();
    expect(screen.queryByTestId('ai-not-ready-banner')).not.toBeInTheDocument();
    expect(container.querySelector('.ant-alert')).toBeNull();
  });

  // Starting a run is admin-only on the server (AuthInterceptor.requiresAdmin) because the agent's
  // tool calls are signed with the instance credential, not the caller's identity. These pin the
  // client half: refuse up front and explain, instead of answering 403 to a typed message.
  it('refusesToSendForAReadOnlyRoleAndSaysWhyTest', () => {
    const { container } = renderComposer({ readOnlyRole: true, value: '列出该实例的 Topic' });

    expect(sendStop()).toHaveAttribute('data-state', 'send');
    expect(sendStop()).toBeDisabled();
    expect(screen.getByTestId('ai-read-only-role-banner')).toBeInTheDocument();
    // Neither of the other two may render: a read-only account cannot read the LLM runtime at all,
    // so both would point at a settings page that answers 403 for them.
    expect(screen.queryByTestId('ai-not-ready-banner')).not.toBeInTheDocument();
    expect(screen.queryByTestId('ai-settings-hint-banner')).not.toBeInTheDocument();
    expect(container.querySelector('.ant-alert')).toBeNull();
  });

  it('doesNotSendOnEnterForAReadOnlyRoleTest', async () => {
    const user = userEvent.setup();
    const { props } = renderComposer({ readOnlyRole: true, value: '列出该实例的 Topic' });

    await user.click(textarea());
    await user.keyboard('{Enter}');

    expect(props.onSend).not.toHaveBeenCalled();
  });

  it('letsTheSameAccountSendOnceTheRoleAllowsItTest', () => {
    const { rerenderWith } = renderComposer({ readOnlyRole: true, value: '列出该实例的 Topic' });
    expect(sendStop()).toBeDisabled();

    rerenderWith({ readOnlyRole: false });

    expect(sendStop()).toBeEnabled();
    expect(screen.queryByTestId('ai-read-only-role-banner')).not.toBeInTheDocument();
    expect(screen.queryByTestId('ai-settings-hint-banner')).not.toBeInTheDocument();
  });

  it('exposesTheThreeToolbarEntriesTest', async () => {
    const user = userEvent.setup();
    const { props } = renderComposer();

    await user.click(screen.getByRole('button', { name: 'AI 对话历史' }));
    await user.click(screen.getByRole('button', { name: /工具/ }));
    await user.click(screen.getByRole('button', { name: '模板' }));
    await user.click(screen.getByRole('button', { name: 'Prompt 增强' }));

    expect(props.onOpenHistory).toHaveBeenCalledTimes(1);
    expect(props.onOpenTools).toHaveBeenCalledTimes(1);
    expect(props.onOpenPromptTemplates).toHaveBeenCalledTimes(1);
    expect(props.onEnhanceChange).toHaveBeenCalledWith(true);
  });
});
