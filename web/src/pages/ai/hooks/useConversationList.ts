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
import { listConversations } from '../../../api/aiConversations';
import type { AiConversationListItemVO } from '../../../api/aiEvents';
import { describeThrownMessage } from '../../../utils/apiError';

/**
 * Server-paged conversation list.
 *
 * History moved to the database, so this replaces the `sessionStorage` recents list: paging,
 * searching and the archived filter are all server-side now, and the hook owns nothing but the
 * query state plus the stale-response guard.
 *
 * Conventions kept from `components/MessageQueryHistoryDrawer.tsx`:
 * - a monotonic `requestId` ref, re-read after every await, so a slow response for an older query
 *   can never overwrite a newer one;
 * - `loading` cleared only by the request that is still current;
 * - any filter change resets to page 1, because page 7 of a different search does not exist.
 */

/**
 * 15 rows keep one page inside a 900px-tall window without an inner scrollbar: a row is 39px, so
 * 15 of them plus the modal's header, toolbar and pager land at ~800px, while 20 rows forced the
 * table body to scroll inside the modal.
 */
export const CONVERSATION_PAGE_SIZE = 15;

export interface UseConversationListResult {
  items: AiConversationListItemVO[];
  total: number;
  /** 1-based, matching antd's pagination. */
  page: number;
  pageSize: number;
  search: string;
  archived: boolean;
  loading: boolean;
  /** Server-supplied message, or `''`; the caller pairs it with an i18n fallback. */
  error: string;
  setPage: (page: number) => void;
  /** Sets the title filter and jumps back to page 1. */
  setSearch: (search: string) => void;
  /** Switches between active and archived conversations and jumps back to page 1. */
  setArchived: (archived: boolean) => void;
  /** Re-runs the current query, e.g. after renaming or deleting a conversation. */
  reload: () => void;
}

export function useConversationList(
  pageSize: number = CONVERSATION_PAGE_SIZE,
): UseConversationListResult {
  const [items, setItems] = useState<AiConversationListItemVO[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [search, setSearchValue] = useState('');
  const [archived, setArchivedValue] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const requestId = useRef(0);

  const load = useCallback(async () => {
    const id = ++requestId.current;
    setLoading(true);
    setError('');
    try {
      const result = await listConversations({
        page,
        size: pageSize,
        search: search || undefined,
        archived,
      });
      if (id !== requestId.current) return;
      setItems(result.items);
      setTotal(result.total);
    } catch (loadError) {
      if (id !== requestId.current) return;
      setItems([]);
      setTotal(0);
      setError(describeThrownMessage(loadError));
    } finally {
      if (id === requestId.current) setLoading(false);
    }
  }, [archived, page, pageSize, search]);

  useEffect(() => {
    // Loading is asynchronous; state updates happen after the conversations API resolves.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
    return () => {
      requestId.current += 1;
    };
  }, [load]);

  const setSearch = useCallback((next: string) => {
    setSearchValue(next);
    setPage(1);
  }, []);

  const setArchived = useCallback((next: boolean) => {
    setArchivedValue(next);
    setPage(1);
  }, []);

  // Re-running `load` is safe even while the previous query is still in flight: the requestId ref
  // makes the older response a no-op.
  const reload = useCallback(() => {
    void load();
  }, [load]);

  return {
    items,
    total,
    page,
    pageSize,
    search,
    archived,
    loading,
    error,
    setPage,
    setSearch,
    setArchived,
    reload,
  };
}
