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

export const FALLBACK_MODELS: Record<string, string[]> = {
  openai: ['gpt-4o', 'gpt-4-turbo', 'gpt-4', 'gpt-3.5-turbo'],
  azure: ['gpt-4o', 'gpt-4', 'gpt-3.5-turbo'],
  deepseek: ['deepseek-chat', 'deepseek-reasoner'],
  tongyi: ['qwen-max', 'qwen-plus', 'qwen-turbo'],
  ollama: ['llama3', 'mistral', 'gemma2', 'qwen2.5'],
  bedrock: ['anthropic.claude-3-sonnet', 'anthropic.claude-3-haiku', 'meta.llama3-70b'],
};

export function fallbackModelOptions(provider: string, model?: string) {
  const fallback = FALLBACK_MODELS[provider] || [model || ''].filter(Boolean);
  return fallback.map((item) => ({ value: item, label: item }));
}
