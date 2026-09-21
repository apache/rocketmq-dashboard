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
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from 'antd';
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { LangProvider } from '../../../i18n/LangContext';
import { attachRunStream, executeTool, listTools, openRunStream } from '../../../api/ai';
import {
  createConversation,
  getAgentCapabilities,
  getConversationTimeline,
  listConversations,
  stopRun,
} from '../../../api/aiConversations';
import type {
  AiConversationVO,
  AiTimelineVO,
  TimelineEvent,
  TimelineItem,
} from '../../../api/aiEvents';
import { listClusters, type ClusterInfo } from '../../../api/cluster';
import { getLlmConfig, getLlmModels } from '../../../api/llm';
import useAuthStore from '../../../stores/authStore';
import { useEngineStore } from '../../../stores/engineStore';
import AiPage from '../index';

/**
 * The page shell, end to end: route param in, hooks wired, `ChatThread` + `Composer` out.
 *
 * The properties worth pinning here (the units live in the hook and component tests):
 * 1. a cold load of `/ai/c/{id}` renders the PERSISTED timeline, not a client-side cache;
 * 2. a send streams run_started → deltas → done and the persisted rows replace the live bubble;
 * 3. navigating away mid-run does not kill it — coming back re-attaches via `/runs/{id}/stream`
 *    with the replay cursor of what is already rendered;
 * 4. the server's 409 to a double send surfaces, and the composer never admits a second send
 *    while one is in flight;
 * 5. `rmqctlAvailable:false` renders the NEUTRAL notice (InfoBanner treatment, no yellow Alert);
 * 6. the home-page draft handoff POSTs the conversation, replace-navigates to `/ai/c/{id}`,
 *    auto-sends EXACTLY once and strips the router state so a reload cannot replay the prompt.
 */

const dataModeMocks = vi.hoisted(() => ({ useMock: false }));

vi.mock('../../../api/ai', () => ({
  AiStreamError: class AiStreamError extends Error {},
  attachRunStream: vi.fn(),
  executeTool: vi.fn(),
  listTools: vi.fn(),
  openRunStream: vi.fn(),
}));

vi.mock('../../../api/aiConversations', () => ({
  createConversation: vi.fn(),
  getAgentCapabilities: vi.fn(),
  getConversationTimeline: vi.fn(),
  listConversations: vi.fn(),
  stopRun: vi.fn(),
}));

vi.mock('../../../api/llm', () => ({
  getLlmConfig: vi.fn(),
  getLlmModels: vi.fn(),
}));

vi.mock('../../../api/cluster', () => ({
  listClusters: vi.fn(),
}));

vi.mock('../../../stores/dataModeStore', () => ({
  useDataModeStore: (selector: (state: typeof dataModeMocks) => unknown) => selector(dataModeMocks),
}));

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
  Element.prototype.scrollIntoView = vi.fn();
});

/* ─── Route harness ─── */

/** Navigation controls so a test can leave a run mid-flight and come back to it. */
const NavProbe = () => {
  const navigate = useNavigate();
  return (
    <div>
      <button type="button" onClick={() => navigate('/ai')}>
        probe-bare
      </button>
      <button type="button" onClick={() => navigate('/ai/c/7')}>
        probe-conversation
      </button>
    </div>
  );
};

/** The live location, so the replace-navigation dance is assertable from the test. */
const LocationProbe = () => {
  const location = useLocation();
  return (
    <div data-testid="probe-location">
      {JSON.stringify({ pathname: location.pathname, state: location.state })}
    </div>
  );
};

const readProbeLocation = (): { pathname: string; state: unknown } =>
  JSON.parse(screen.getByTestId('probe-location').textContent ?? '{}');

const renderRouted = (path: string, state?: unknown) =>
  render(
    <App>
      <LangProvider>
        <MemoryRouter initialEntries={[state === undefined ? path : { pathname: path, state }]}>
          <NavProbe />
          <LocationProbe />
          <Routes>
            <Route path="/ai" element={<AiPage />} />
            <Route path="/ai/c/:conversationId" element={<AiPage />} />
          </Routes>
        </MemoryRouter>
      </LangProvider>
    </App>,
  );

/** The harness of the pre-persistence suite: the bare `/ai` route with optional router state. */
const renderPage = (state?: unknown) => renderRouted('/ai', state);

/* ─── Fixtures ─── */

function item(seq: number, event: TimelineEvent, runId = 41): TimelineItem {
  return { id: seq, turn: 1, seq, runId, createdAt: '2026-09-20T02:12:00', event };
}

function timelinePage(
  items: TimelineItem[],
  activeRun: AiTimelineVO['activeRun'] = null,
  nextAfter: number | null = null,
): AiTimelineVO {
  return { items, nextAfter, activeRun };
}

const CONVERSATION_7: AiConversationVO = {
  id: 7,
  title: '查看集群状态',
  owner: 'admin',
  engine: 'claude-code',
  model: 'gpt-4o',
  mode: 'chat',
  lastSeq: 0,
  archived: false,
  createdAt: '2026-09-20T02:12:00',
  updatedAt: '2026-09-20T02:12:00',
};

const PLACEHOLDER = '输入你的问题或指令，例如：查看集群状态、创建 Topic、诊断消费延迟...';

/** Let the requestAnimationFrame-coalesced tick of `useAgentRun` fire (jsdom paints at ~16ms). */
async function flushFrame(): Promise<void> {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 32));
  });
}

async function typeAndWaitForReady(text: string): Promise<HTMLElement> {
  const input = await screen.findByPlaceholderText(PLACEHOLDER);
  await waitFor(() => expect(getLlmModels).toHaveBeenCalled());
  fireEvent.change(input, { target: { value: text } });
  return input;
}

describe('AiPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    dataModeMocks.useMock = false;
    localStorage.clear();
    sessionStorage.clear();
    useAuthStore.setState({ user: null, userId: null, admin: null });
    useEngineStore.setState({ engine: 'claude-code' });
    vi.mocked(getLlmConfig).mockResolvedValue({
      provider: 'openai',
      apiBase: 'https://api.openai.com/v1',
      model: 'gpt-4o',
      maxTokens: 1024,
      temperature: 0.2,
      enabled: true,
      ready: true,
    });
    vi.mocked(getLlmModels).mockResolvedValue({ status: 0, data: [{ id: 'gpt-4o' }] });
    vi.mocked(getAgentCapabilities).mockResolvedValue({
      rmqctlAvailable: true,
      claudeAvailable: true,
      qoderAvailable: false,
      mcpEnabled: true,
      l3ToolsAllowed: false,
    });
    vi.mocked(getConversationTimeline).mockResolvedValue(timelinePage([]));
    vi.mocked(createConversation).mockResolvedValue(CONVERSATION_7);
    vi.mocked(listConversations).mockResolvedValue({ items: [], total: 0, page: 1, size: 20 });
    vi.mocked(stopRun).mockResolvedValue({
      id: 41,
      conversationId: 7,
      turn: 1,
      status: 'STOPPED',
      engine: 'claude-code',
      model: 'gpt-4o',
      stopReason: 'USER_STOP',
    });
    vi.mocked(listClusters).mockResolvedValue([]);
    vi.mocked(listTools).mockResolvedValue([]);
  });

  it('rendersThePersistedTimelineOnAColdLoadTest', async () => {
    vi.mocked(getConversationTimeline).mockResolvedValue(
      timelinePage([
        item(1, { type: 'user', text: '查看集群路由' }),
        item(2, { type: 'thinking', text: '先看 topic 路由', source: 'model' }),
        item(3, { type: 'text', text: '集群当前有 2 个 broker。' }),
        item(4, { type: 'run_status', status: 'COMPLETED' }),
      ]),
    );

    renderRouted('/ai/c/7');

    expect(await screen.findByText('查看集群路由')).toBeInTheDocument();
    expect(await screen.findByText('集群当前有 2 个 broker。')).toBeInTheDocument();
    // Model reasoning comes back as a collapsed 思考过程 disclosure — NOT mislabelled as an
    // enhancement rewrite, which was the bug the source field exists to fix.
    const thinking = screen.getByTestId('ai-thinking-block');
    expect(within(thinking).getByTestId('ai-thinking-summary')).toHaveTextContent('思考过程');
    expect(getConversationTimeline).toHaveBeenCalledWith(7, { after: 0, limit: 200 });
    expect(openRunStream).not.toHaveBeenCalled();
    expect(attachRunStream).not.toHaveBeenCalled();
  });

  it('streamsRunStartedThenDeltasThenDoneAndHandsTheBubbleToHistoryTest', async () => {
    let release!: () => void;
    const held = new Promise<void>((resolve) => {
      release = resolve;
    });
    vi.mocked(openRunStream).mockImplementation(async (_cid, _request, handlers) => {
      handlers.onEvent({
        type: 'run_started',
        runId: 41,
        conversationId: 7,
        title: '打个招呼',
        turn: 1,
      });
      handlers.onEvent({ type: 'text_delta', content: '你好，' });
      handlers.onEvent({ type: 'text_delta', content: '世界' });
      await held;
      handlers.onEvent({ type: 'run_finished', runId: 41, status: 'COMPLETED', durationMs: 12 });
    });
    renderRouted('/ai/c/7');
    const input = await typeAndWaitForReady('打个招呼');

    fireEvent.keyDown(input, { key: 'Enter' });

    await waitFor(() => expect(openRunStream).toHaveBeenCalledTimes(1));
    expect(openRunStream).toHaveBeenCalledWith(
      7,
      expect.objectContaining({ message: '打个招呼', engine: 'claude-code', mode: 'chat' }),
      expect.anything(),
      expect.anything(),
    );
    // The coalesced live blocks paint, and the single button slot morphs to stop.
    expect(await screen.findByText('你好，世界')).toBeInTheDocument();
    expect(screen.getByTestId('ai-send-stop-button')).toHaveAttribute('data-state', 'stop');

    // The run finishes: the refetched persisted rows replace the live bubble in one paint.
    vi.mocked(getConversationTimeline).mockResolvedValue(
      timelinePage([
        item(1, { type: 'user', text: '打个招呼' }),
        item(2, { type: 'text', text: '你好，世界' }),
        item(3, { type: 'run_status', status: 'COMPLETED' }),
      ]),
    );
    await act(async () => {
      release();
    });

    await waitFor(() => expect(screen.getAllByText('你好，世界')).toHaveLength(1));
    expect(screen.getByTestId('ai-send-stop-button')).toHaveAttribute('data-state', 'send');
  });

  it('attachesToTheStillRunningRunAfterNavigatingAwayAndBackTest', async () => {
    const user = userEvent.setup();
    vi.mocked(openRunStream).mockImplementation(
      (_cid, _request, handlers, signal) =>
        new Promise<void>((resolve) => {
          handlers.onEvent({
            type: 'run_started',
            runId: 41,
            conversationId: 7,
            title: 'long question',
            turn: 1,
          });
          handlers.onEvent({ type: 'text_delta', content: 'streaming answer' });
          if (signal) signal.addEventListener('abort', () => resolve());
          else resolve();
        }),
    );
    vi.mocked(attachRunStream).mockResolvedValue(undefined);
    renderRouted('/ai/c/7');
    const input = await typeAndWaitForReady('long question');

    fireEvent.keyDown(input, { key: 'Enter' });
    await waitFor(() =>
      expect(screen.getByTestId('ai-send-stop-button')).toHaveAttribute('data-state', 'stop'),
    );

    // Leaving aborts OUR reader only; the run keeps generating server-side.
    await user.click(screen.getByRole('button', { name: 'probe-bare' }));
    await waitFor(() => expect(readProbeLocation().pathname).toBe('/ai'));
    expect(screen.getByTestId('ai-send-stop-button')).toHaveAttribute('data-state', 'send');

    // Coming back, the timeline reports the active run and the page re-attaches with the replay
    // cursor of what is already persisted (seq 2), so nothing renders twice.
    vi.mocked(getConversationTimeline).mockResolvedValue(
      timelinePage(
        [item(1, { type: 'user', text: 'long question' }), item(2, { type: 'text', text: '…' })],
        { id: 41, status: 'RUNNING' },
      ),
    );
    await user.click(screen.getByRole('button', { name: 'probe-conversation' }));

    await waitFor(() => expect(attachRunStream).toHaveBeenCalledTimes(1));
    expect(attachRunStream).toHaveBeenCalledWith(41, 2, expect.anything(), expect.anything());
    // The attach stream resolving while the run is STILL active must not loop.
    await flushFrame();
    await waitFor(() =>
      expect(getConversationTimeline).toHaveBeenCalledWith(7, {
        after: 0,
        limit: 200,
      }),
    );
    expect(attachRunStream).toHaveBeenCalledTimes(1);
  });

  it('surfacesTheServerRejectionOfADoubleSendTest', async () => {
    vi.mocked(openRunStream).mockRejectedValue(
      Object.assign(new Error('该会话已有正在进行的回答'), {
        name: 'AiStreamError',
        code: 'ai.run.in_flight',
        status: 409,
      }),
    );
    renderRouted('/ai/c/7');
    const input = await typeAndWaitForReady('第二次发送');

    fireEvent.keyDown(input, { key: 'Enter' });

    expect(await screen.findByTestId('ai-run-error')).toHaveTextContent('该会话已有正在进行的回答');
    // The button slot is back to send: a failed send is not a run in flight.
    await waitFor(() =>
      expect(screen.getByTestId('ai-send-stop-button')).toHaveAttribute('data-state', 'send'),
    );
  });

  it('givesTheDraftBackWhenTheServerRefusesTheSendTest', async () => {
    vi.mocked(openRunStream).mockRejectedValue(
      Object.assign(new Error('该会话已有正在进行的回答'), {
        name: 'AiStreamError',
        code: 'ai.run.in_flight',
        status: 409,
      }),
    );
    renderRouted('/ai/c/7');
    const input = await typeAndWaitForReady('第二次发送');

    fireEvent.keyDown(input, { key: 'Enter' });

    await waitFor(() => expect(openRunStream).toHaveBeenCalledTimes(1));
    // The composer clears the draft on send and the caller must put it back when the send was
    // refused; otherwise the operator retypes a prompt that never left the browser.
    await waitFor(() => expect(input).toHaveValue('第二次发送'));
  });

  it('keepsTheHandoffDraftWhenTheSendIsRefusedTest', async () => {
    vi.mocked(openRunStream).mockRejectedValue(
      Object.assign(new Error('该会话已有正在进行的回答'), {
        name: 'AiStreamError',
        code: 'ai.run.in_flight',
        status: 409,
      }),
    );

    renderRouted('/ai/c/7', { prompt: '检查集群状态', mode: 'chat' });

    await waitFor(() => expect(openRunStream).toHaveBeenCalledTimes(1));
    expect(screen.getByPlaceholderText(PLACEHOLDER)).toHaveValue('检查集群状态');
  });

  it('keepsTheComposerClearedWhenAnAdmittedStreamFailsTest', async () => {
    // The other side of "a refused send gives the draft back": a stream that already delivered a
    // frame WAS admitted, so a failure mid-answer must not resurrect the prompt as if nothing had
    // been sent (this one pins the boundary; it also passes before the fix).
    vi.mocked(openRunStream).mockImplementation(async (_cid, _request, handlers) => {
      handlers.onEvent({
        type: 'run_started',
        runId: 41,
        conversationId: 7,
        title: '检查集群状态',
        turn: 1,
      });
      handlers.onEvent({ type: 'text_delta', content: '部分回答' });
      throw new Error('AI stream idle for more than 30s');
    });
    renderRouted('/ai/c/7');
    const input = await typeAndWaitForReady('检查集群状态');

    fireEvent.keyDown(input, { key: 'Enter' });

    expect(await screen.findByTestId('ai-run-error')).toHaveTextContent(
      'AI stream idle for more than 30s',
    );
    expect(input).toHaveValue('');
  });

  it('doesNotAdmitASecondSendWhileOneIsInFlightTest', async () => {
    vi.mocked(openRunStream).mockReturnValue(new Promise(() => {}));
    renderRouted('/ai/c/7');
    const input = await typeAndWaitForReady('检查集群状态');

    await act(async () => {
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    });

    expect(openRunStream).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId('ai-send-stop-button')).toHaveAttribute('data-state', 'stop');
  });

  it('rendersTheNeutralNoticeWhenRmqctlIsUnavailableTest', async () => {
    vi.mocked(getAgentCapabilities).mockResolvedValue({
      rmqctlAvailable: false,
      claudeAvailable: true,
      qoderAvailable: false,
      mcpEnabled: true,
      l3ToolsAllowed: false,
    });

    renderPage();

    const banner = await screen.findByTestId('ai-rmqctl-unavailable-banner');
    expect(banner).toHaveTextContent('Agent 工具通道不可用');
    // A missing capability is a persistent statement about the runtime, not a semantic warning:
    // the neutral InfoBanner treatment, never a coloured antd Alert.
    expect(banner.closest('.ant-alert')).toBeNull();
    expect(document.querySelector('.ant-alert-warning')).toBeNull();
  });

  it('handsOffTheHomePageDraftIntoANewConversationAndAutoSendsItExactlyOnceTest', async () => {
    vi.mocked(openRunStream).mockResolvedValue(undefined);

    renderPage({
      prompt: '检查集群状态',
      mode: 'diagnose',
      enhance: true,
      engine: 'qoder',
      model: 'qwen3.8-max',
      instanceId: 'rmq-instance-1',
    });

    // The conversation is created server-side, pinned to the instance and mode of the draft.
    await waitFor(() => expect(createConversation).toHaveBeenCalledTimes(1));
    expect(createConversation).toHaveBeenCalledWith({
      instanceId: 'rmq-instance-1',
      mode: 'diagnose',
    });

    // The route moved to the deep link and the draft auto-sent — once, on the resolved id.
    await waitFor(() => expect(openRunStream).toHaveBeenCalledTimes(1));
    expect(openRunStream).toHaveBeenCalledWith(
      7,
      expect.objectContaining({
        message: '检查集群状态',
        mode: 'diagnose',
        enhance: true,
        engine: 'qoder',
        model: 'qwen3.8-max',
      }),
      expect.anything(),
      expect.anything(),
    );
    expect(useEngineStore.getState().engine).toBe('qoder');

    // The replace-navigation dance ends with the draft stripped from the history entry, so a
    // reload of /ai/c/7 cannot replay (and re-send) the prompt.
    await waitFor(() => {
      const probe = readProbeLocation();
      expect(probe.pathname).toBe('/ai/c/7');
      expect(probe.state).toBeNull();
    });
    expect(openRunStream).toHaveBeenCalledTimes(1);
  });

  it('opensTheHistoryDrawerOnceForTheHistoryRouteIntentTest', async () => {
    renderPage({ historyIntent: 'open' });

    const drawer = await screen.findByRole('dialog', { name: 'AI 对话历史' });
    expect(within(drawer).getByText('暂无会话')).toBeInTheDocument();
    await waitFor(() => expect(readProbeLocation().state).toBeNull());
    expect(openRunStream).not.toHaveBeenCalled();
  });

  it('navigatesToTheDeepLinkWhenAConversationIsPickedFromHistoryTest', async () => {
    const user = userEvent.setup();
    vi.mocked(listConversations).mockResolvedValue({
      items: [
        {
          id: 9,
          title: '历史会话',
          engine: 'claude-code',
          model: 'gpt-4o',
          mode: 'chat',
          updatedAt: '2026-09-20T02:12:00',
          createdAt: '2026-09-20T02:12:00',
        },
      ],
      total: 1,
      page: 1,
      size: 20,
    });
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'AI 对话历史' }));
    const drawer = await screen.findByRole('dialog', { name: 'AI 对话历史' });
    await user.click(within(drawer).getByTestId('ai-conversation-link-9'));

    await waitFor(() => expect(readProbeLocation().pathname).toBe('/ai/c/9'));
    await waitFor(() =>
      expect(getConversationTimeline).toHaveBeenCalledWith(9, { after: 0, limit: 200 }),
    );
    expect(openRunStream).not.toHaveBeenCalled();
  });

  it('doesNotLoadTheLlmRuntimeInMockModeAndDisablesTheComposerTest', async () => {
    dataModeMocks.useMock = true;
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByTestId('ai-mock-disabled')).toHaveTextContent(
      'Mock 模式已禁用 AI Provider 调用',
    );
    expect(getLlmConfig).not.toHaveBeenCalled();
    expect(getLlmModels).not.toHaveBeenCalled();
    expect(getAgentCapabilities).not.toHaveBeenCalled();
    expect(screen.getByTestId('ai-send-stop-button')).toBeDisabled();

    await user.click(screen.getByRole('button', { name: '工具' }));
    await waitFor(() => expect(listClusters).not.toHaveBeenCalled());
    expect(listTools).not.toHaveBeenCalled();
  });

  it('degradesForReaderAccountsWithoutLoadingModelConfigurationTest', async () => {
    useAuthStore.setState({ user: 'reader', userId: 9, admin: false });
    renderPage();

    await waitFor(() => {
      expect(getLlmConfig).not.toHaveBeenCalled();
      expect(getLlmModels).not.toHaveBeenCalled();
    });
    // Nothing ready → no send; the composer says so instead of pretending.
    expect(screen.getByTestId('ai-send-stop-button')).toBeDisabled();
  });

  it('appliesABuiltinPromptTemplateWithItsModeAndEnhancementSettingTest', async () => {
    vi.mocked(openRunStream).mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderPage();
    const input = await typeAndWaitForReady('');

    await user.click(screen.getByRole('button', { name: '模板' }));
    const dialog = await screen.findByRole('dialog', { name: 'Prompt 模板' });
    expect(within(dialog).getByText('消费延迟诊断')).toBeInTheDocument();
    // antd 对纯两字中文按钮自动插空格（「使 用」），用正则容错
    await user.click(within(dialog).getAllByRole('button', { name: /使\s*用/ })[0]);

    expect((input as HTMLTextAreaElement).value).toContain(
      '请诊断当前 RocketMQ 实例中的消费延迟问题',
    );
    expect(screen.getAllByTitle('对话模式')[0]).toHaveTextContent('诊断');
    // The enhance toggle uses the home page's active style (soft purple fill, not a border).
    expect(screen.getByTitle('发送前增强 Prompt')).toHaveStyle({ background: '#f9f0ff' });

    // Sending from the bare route creates the conversation first and auto-sends on the new id.
    fireEvent.keyDown(input, { key: 'Enter' });
    await waitFor(() => expect(createConversation).toHaveBeenCalledTimes(1));
    await waitFor(() =>
      expect(openRunStream).toHaveBeenCalledWith(
        7,
        expect.objectContaining({
          message: expect.stringContaining('请诊断当前 RocketMQ 实例中的消费延迟问题'),
          mode: 'diagnose',
          enhance: true,
        }),
        expect.anything(),
        expect.anything(),
      ),
    );
  });

  it('opensTheToolPlaygroundLoadsTheCatalogAndExecutesAToolTest', async () => {
    const user = userEvent.setup();
    vi.mocked(listClusters).mockResolvedValue([
      { id: 'cluster-a', name: 'Cluster A' } as ClusterInfo,
    ]);
    vi.mocked(listTools).mockResolvedValue([
      {
        name: 'rmq.instance.capabilities',
        description: 'Describe instance capabilities.',
        parameters: {
          type: 'object',
          required: ['instanceId'],
          properties: { instanceId: { type: 'string' } },
        },
        riskLevel: 'L1',
        permission: 'cluster:read',
      },
    ]);
    vi.mocked(executeTool).mockResolvedValue({ instanceId: 'cluster-a', capabilities: ['GRPC'] });
    renderPage();
    await waitFor(() => expect(getLlmModels).toHaveBeenCalled());

    await user.click(screen.getByRole('button', { name: '工具' }));
    const dialog = await screen.findByRole('dialog', { name: 'AI 工具' });
    await waitFor(() => expect(listTools).toHaveBeenCalledWith('cluster-a'));
    expect(within(dialog).getByText('rmq.instance.capabilities')).toBeInTheDocument();
    expect(within(dialog).getByText('L1')).toBeInTheDocument();

    const toolInput = within(dialog).getByRole('textbox', { name: '工具参数 JSON' });
    expect(toolInput).toHaveValue('{\n  "instanceId": "cluster-a"\n}');
    await user.click(within(dialog).getByRole('button', { name: /执\s*行/ }));

    await waitFor(() =>
      expect(executeTool).toHaveBeenCalledWith(
        'rmq.instance.capabilities',
        { instanceId: 'cluster-a' },
        'cluster-a',
      ),
    );
    expect(await within(dialog).findByTestId('tool-result')).toHaveTextContent('"GRPC"');
  });

  it('rejectsToolInputThatIsNotAJsonObjectTest', async () => {
    const user = userEvent.setup();
    vi.mocked(listClusters).mockResolvedValue([
      { id: 'cluster-a', name: 'Cluster A' } as ClusterInfo,
    ]);
    vi.mocked(listTools).mockResolvedValue([
      { name: 'rmq.topic.list', description: 'List topics.', parameters: {} },
    ]);
    renderPage();
    await waitFor(() => expect(getLlmModels).toHaveBeenCalled());

    await user.click(screen.getByRole('button', { name: '工具' }));
    const dialog = await screen.findByRole('dialog', { name: 'AI 工具' });
    const toolInput = await within(dialog).findByRole('textbox', { name: '工具参数 JSON' });
    fireEvent.change(toolInput, { target: { value: '[]' } });
    await user.click(within(dialog).getByRole('button', { name: /执\s*行/ }));

    expect(await screen.findByText('工具参数必须是有效的 JSON 对象')).toBeInTheDocument();
    expect(executeTool).not.toHaveBeenCalled();
  });

  it('doesNotSendWhileAnInputMethodCompositionIsBeingConfirmedTest', async () => {
    renderRouted('/ai/c/7');
    const input = await typeAndWaitForReady('检查集群状态');

    fireEvent.keyDown(input, { key: 'Enter', isComposing: true });

    expect(openRunStream).not.toHaveBeenCalled();
    expect((input as HTMLTextAreaElement).value).toBe('检查集群状态');
  });
});
