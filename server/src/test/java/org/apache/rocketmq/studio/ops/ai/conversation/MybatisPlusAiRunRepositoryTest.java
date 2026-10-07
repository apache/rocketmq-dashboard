/*
 * Licensed to the Apache Software Foundation (ASF) under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.studio.ops.ai.conversation;

import org.apache.rocketmq.studio.persistence.entity.RmqAiRun;
import org.apache.rocketmq.studio.persistence.mapper.RmqAiRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MybatisPlusAiRunRepositoryTest {

    private RmqAiRunMapper runMapper;
    private MybatisPlusAiRunRepository repository;

    @BeforeEach
    void setUp() {
        runMapper = mock(RmqAiRunMapper.class);
        repository = new MybatisPlusAiRunRepository(runMapper);
    }

    private static RmqAiRun run(Long id, Long conversationId, String status) {
        RmqAiRun run = new RmqAiRun();
        run.setId(id);
        run.setConversationId(conversationId);
        run.setStatus(status);
        return run;
    }

    @Test
    void aNullIdFindsNothingWithoutTouchingTheMapper() {
        assertThat(repository.findById(null)).isEmpty();
        verify(runMapper, never()).selectById(null);
    }

    @Test
    void insertReturnsTheRowItWasGiven() {
        RmqAiRun run = run(1L, 7L, "RUNNING");

        RmqAiRun inserted = repository.insert(run);

        assertThat(inserted).isSameAs(run);
        verify(runMapper).insert(run);
    }

    @Test
    void findActiveUsesTheRunStatusVocabulary() {
        when(runMapper.selectList(any())).thenReturn(List.of(run(5L, 7L, "RUNNING")));

        Optional<RmqAiRun> active = repository.findActiveByConversationId(7L);

        assertThat(active).isPresent();
        assertThat(active.get().getId()).isEqualTo(5L);
    }

    @Test
    void aNullConversationFindsNoActiveRun() {
        assertThat(repository.findActiveByConversationId(null)).isEmpty();
        verify(runMapper, never()).selectList(any());
    }

    @Test
    void aNullConversationListsNoRunsAndDeletesNothing() {
        assertThat(repository.findByConversationId(null)).isEmpty();
        assertThat(repository.deleteByConversationId(null)).isZero();
        verify(runMapper, never()).selectList(any());
        verify(runMapper, never()).delete(any());
    }

    @Test
    void findStaleActiveRequiresBothTheCutoffAndTheStatuses() {
        assertThat(repository.findStaleActive(null, List.of("RUNNING"))).isEmpty();
        assertThat(repository.findStaleActive(LocalDateTime.now(), null)).isEmpty();
        assertThat(repository.findStaleActive(LocalDateTime.now(), List.of())).isEmpty();
        verify(runMapper, never()).selectList(any());
    }

    @Test
    void findByStatusInRequiresStatuses() {
        assertThat(repository.findByStatusIn(null)).isEmpty();
        assertThat(repository.findByStatusIn(List.of())).isEmpty();
        verify(runMapper, never()).selectList(any());
    }

    @Test
    void deleteByConversationIdsRequiresIds() {
        assertThat(repository.deleteByConversationIds(null)).isZero();
        assertThat(repository.deleteByConversationIds(List.of())).isZero();
        verify(runMapper, never()).delete(any());
    }

    @Test
    void findLatestByConversationIdsAggregatesWithoutPerConversationQueries() {
        // The page asks for three conversations; the repository must answer with two
        // statements (one MAX(id) group-by, one selectList), never one per conversation.
        when(runMapper.selectObjs(any())).thenReturn(List.of(10L, 20L));
        when(runMapper.selectList(any())).thenReturn(List.of(
                run(10L, 7L, "COMPLETED"),
                run(20L, 9L, "RUNNING")));

        Map<Long, RmqAiRun> latest = repository.findLatestByConversationIds(List.of(7L, 8L, 9L));

        assertThat(latest).containsOnlyKeys(7L, 9L);
        assertThat(latest.get(7L).getId()).isEqualTo(10L);
        assertThat(latest.get(9L).getId()).isEqualTo(20L);
        verify(runMapper).selectObjs(any());
        verify(runMapper).selectList(any());
    }

    @Test
    void findLatestBreaksUnexpectedTiesTowardsTheNewerRun() {
        when(runMapper.selectObjs(any())).thenReturn(List.of(10L, 20L));
        // A duplicate that should not happen: both rows claim conversation 7.
        when(runMapper.selectList(any())).thenReturn(List.of(
                run(10L, 7L, "COMPLETED"),
                run(15L, 7L, "COMPLETED")));

        Map<Long, RmqAiRun> latest = repository.findLatestByConversationIds(List.of(7L));

        assertThat(latest.get(7L).getId()).isEqualTo(15L);
    }

    @Test
    void findLatestFiltersNullConversationsAndRowsWithoutOne() {
        when(runMapper.selectObjs(any())).thenReturn(List.of(10L));
        // A row whose conversation id is null cannot be keyed; it is skipped, not crashed on.
        when(runMapper.selectList(any())).thenReturn(List.of(run(10L, null, "COMPLETED")));

        Map<Long, RmqAiRun> latest = repository.findLatestByConversationIds(java.util.Arrays.asList(7L, null));

        assertThat(latest).isEmpty();
    }

    @Test
    void findLatestWithNoUsableIdsQueriesNothing() {
        assertThat(repository.findLatestByConversationIds(null)).isEmpty();
        assertThat(repository.findLatestByConversationIds(List.of())).isEmpty();
        // All-null input is filtered before any query runs.
        assertThat(repository.findLatestByConversationIds(java.util.Arrays.asList((Long) null))).isEmpty();
        verify(runMapper, never()).selectObjs(any());
    }

    @Test
    void findLatestWithNoMatchesReturnsEmptyWithoutTheSecondQuery() {
        when(runMapper.selectObjs(any())).thenReturn(List.of());

        assertThat(repository.findLatestByConversationIds(List.of(7L))).isEmpty();
        verify(runMapper, never()).selectList(any());
    }

    @Test
    void maxTurnCoalescesNullAndMissingResultsToZero() {
        when(runMapper.selectObjs(any())).thenReturn(List.of());
        assertThat(repository.maxTurn(7L)).isZero();

        when(runMapper.selectObjs(any())).thenReturn(java.util.Arrays.asList((Object) null));
        assertThat(repository.maxTurn(7L)).isZero();

        when(runMapper.selectObjs(any())).thenReturn(List.of(4));
        assertThat(repository.maxTurn(7L)).isEqualTo(4);
    }

    @Test
    void aNullConversationHasMaxTurnZero() {
        assertThat(repository.maxTurn(null)).isZero();
        verify(runMapper, never()).selectObjs(any());
    }
}
