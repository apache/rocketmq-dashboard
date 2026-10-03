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

import { useCallback, useEffect, useRef, useState } from 'react';
import { getLlmConfig, getLlmModels, type LlmConfig } from '../../../api/llm';
import type { AgentEngine } from '../../../stores/engineStore';

/**
 * The provider runtime of the AI page: the LLM config, the selectable models and the current
 * selection.
 *
 * Extracted from the page so the shell stays a shell. The loading rules are the ones the page has
 * always had:
 * - `enabled === false` (mock mode, or an account that may not inspect the LLM runtime) clears
 *   everything and never hits the endpoints — the reader-account degradation is "no configuration
 *   visible", not "an error toast on every page load";
 * - the engine carried by the config bootstraps the engine selector through `onEngine`, which the
 *   page ignores once a home-page draft has already picked one;
 * - a model from the draft is merged into the options by {@link UseLlmRuntimeResult.selectModel}
 *   so a hand-off model the provider list does not (yet) contain still renders as selected.
 */

export interface ModelOption {
  value: string;
  label: string;
}

export interface UseLlmRuntimeOptions {
  /** False in mock mode or for accounts that may not inspect the LLM runtime. */
  enabled: boolean;
  /** Called with the configured engine so the page can bootstrap its engine selector. */
  onEngine?: (engine: AgentEngine) => void;
  /** Any load failure the caller should also toast about. */
  onError?: (error: unknown) => void;
}

export interface UseLlmRuntimeResult {
  config: LlmConfig | null;
  modelOptions: ModelOption[];
  modelsLoading: boolean;
  selectedModel: string;
  /** Provider configured, enabled and a model selected: a send would be accepted. */
  llmReady: boolean;
  setSelectedModel: (model: string) => void;
  /** Select a model and make sure it is among the options, e.g. one carried by a home-page draft. */
  selectModel: (model: string) => void;
  reload: () => void;
}

const isAgentEngine = (value: unknown): value is AgentEngine =>
  value === 'claude-code' || value === 'qoder' || value === 'http';

export function useLlmRuntime(options: UseLlmRuntimeOptions): UseLlmRuntimeResult {
  const { enabled } = options;
  const [config, setConfig] = useState<LlmConfig | null>(null);
  const [modelOptions, setModelOptions] = useState<ModelOption[]>([]);
  const [modelsLoading, setModelsLoading] = useState(false);
  const [selectedModel, setSelectedModel] = useState('');

  // Callbacks arrive as fresh closures on every render; a ref keeps `load` referentially stable so
  // the effect below runs once per `enabled` flip instead of on every render.
  const optionsRef = useRef(options);
  useEffect(() => {
    optionsRef.current = options;
  });

  const selectModel = useCallback((model: string) => {
    setSelectedModel(model);
    setModelOptions((current) =>
      current.some((option) => option.value === model)
        ? current
        : [{ value: model, label: model }, ...current],
    );
  }, []);

  // Monotonic id per load: a response from a superseded load (an enabled flip or an overlapping
  // reload) must not repopulate the state, or a disabled runtime could come back "ready".
  const loadSeqRef = useRef(0);

  const load = useCallback(async () => {
    const requestId = ++loadSeqRef.current;
    if (!enabled) {
      setConfig(null);
      setModelOptions([]);
      setSelectedModel('');
      setModelsLoading(false);
      return;
    }
    setModelsLoading(true);
    try {
      const loaded = await getLlmConfig();
      if (requestId !== loadSeqRef.current) return;
      setConfig(loaded);
      if (isAgentEngine(loaded.engine)) optionsRef.current.onEngine?.(loaded.engine);
      if (loaded.model) setSelectedModel((current) => current || loaded.model);
      const result = await getLlmModels();
      if (requestId !== loadSeqRef.current) return;
      const models = result?.status === 0 && result.data ? result.data : [];
      const options = models
        .map((item) => item.id || item.name || '')
        .filter(Boolean)
        .map((id) => ({ value: id, label: id }));
      if (options.length > 0) {
        setModelOptions(options);
        setSelectedModel((current) => current || loaded.model || options[0].value);
      } else if (loaded.model) {
        setModelOptions([{ value: loaded.model, label: loaded.model }]);
      }
    } catch (error) {
      if (requestId === loadSeqRef.current) optionsRef.current.onError?.(error);
    } finally {
      if (requestId === loadSeqRef.current) setModelsLoading(false);
    }
  }, [enabled]);

  useEffect(() => {
    // Loading is asynchronous; state updates happen after the runtime APIs resolve.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);

  const llmReady = Boolean((config?.ready ?? config?.enabled) && selectedModel);

  return {
    config,
    modelOptions,
    modelsLoading,
    selectedModel,
    llmReady,
    setSelectedModel,
    selectModel,
    reload: load,
  };
}
