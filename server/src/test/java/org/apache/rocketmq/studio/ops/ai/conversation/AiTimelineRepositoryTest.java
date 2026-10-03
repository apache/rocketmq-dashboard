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
package org.apache.rocketmq.studio.ops.ai.conversation;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.ops.ai.LlmConfigService;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.RmqctlWorkspace;
import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;
import org.apache.rocketmq.studio.ops.ai.conversation.event.TimelineEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiConversation;
import org.apache.rocketmq.studio.persistence.entity.RmqAiEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiRun;
import org.apache.rocketmq.studio.persistence.mapper.RmqAiConversationMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqAiEventMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqAiRunMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The SQL the three {@code rmq_ai_*} repositories hand to MyBatis-Plus: the timeline slice
 * {@link MybatisPlusAiEventRepository} reads and the cursor {@link AiConversationService#timeline}
 * derives from it, the owner-scoped conversation list, and the run lookups the admission guard and the
 * two sweeps are built on.
 *
 * <p>One assertion here is load-bearing and easy to mistake for pedantry: the generated SQL must not
 * contain {@code ORDER BY}. {@code rmq_ai_event.payload} is MEDIUMTEXT, and a sort MySQL cannot serve
 * from {@code uk_ai_event_conversation_seq} materialises the whole value of every candidate row into
 * {@code sort_buffer_size} — a timeline read that spills, on the column that is largest in the schema,
 * for an ordering the caller could have produced in memory over a slice of at most 500 rows. The slice
 * is bounded by construction, so it is sorted in Java instead. Someone "tidying up" the query by adding
 * an ordering gets a green functional test and a slower database; these cases are what turns that into
 * a red build.
 *
 * <p>Two further choices are pinned the same way, because both fail in production rather than in a
 * functional test: an empty id collection never reaches the database (MyBatis-Plus renders {@code IN ()}
 * for it, which is a syntax error, and the retention pass that has nothing to do would hit it every
 * time), and the run status vocabulary is {@link RunStatus#ACTIVE_STATUSES} instead of a second copy of
 * the two names.
 *
 * <p>The mappers are mocked rather than backed by H2 so the SQL text itself is the subject. The same
 * queries executed for real, against the same DDL, are {@code AiConversationPersistenceIntegrationTest}.
 */
class AiTimelineRepositoryTest {

    private static final long CONVERSATION_ID = 7L;
    private static final long RUN_ID = 11L;
    private static final String OWNER = "tester";
    private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * The whole where-clause of a timeline slice, in the form MyBatis-Plus renders it: the two index
     * columns of {@code uk_ai_event_conversation_seq} as bound parameters, then the row count. Asserted
     * by equality, not by {@code contains}, so an added ordering shows up in the failure message instead
     * of hiding behind a passing substring.
     */
    private static final String SLICE_SQL = "(conversation_id = ? AND seq > ?) LIMIT ";

    private final RmqAiEventMapper eventMapper = mock(RmqAiEventMapper.class);
    private final RmqAiConversationMapper conversationMapper = mock(RmqAiConversationMapper.class);
    private final RmqAiRunMapper runMapper = mock(RmqAiRunMapper.class);

    private final MybatisPlusAiEventRepository repository = new MybatisPlusAiEventRepository(eventMapper);
    private final MybatisPlusAiConversationRepository conversations =
            new MybatisPlusAiConversationRepository(conversationMapper);
    private final MybatisPlusAiRunRepository runs = new MybatisPlusAiRunRepository(runMapper);

    /** The two ports the service harness below stubs; not under test here. */
    private final AiConversationRepository conversationPort = mock(AiConversationRepository.class);
    private final AiRunRepository runPort = mock(AiRunRepository.class);

    // --- the shape of the query -------------------------------------------------

    @Test
    void theSliceShouldFilterOnTheUniqueKeyAndAskForOneRowMoreThanThePageTest() {
        when(eventMapper.selectList(any())).thenReturn(List.of());

        // 51 for a page of 50: the caller asks for one row beyond the page, and whether that row came
        // back is what says "there is more" without a second round trip.
        repository.findByConversationIdAfterSeq(CONVERSATION_ID, 150, 51);

        QueryWrapper<RmqAiEvent> query = capturedQuery();
        assertThat(query.getTargetSql()).isEqualTo(SLICE_SQL + 51);
        // Both predicates reach the statement as bound parameters — never interpolated into the SQL text
        // — and there is no third one: another filter would be a scan the unique key cannot serve. The
        // raw form is asserted by contains because the parameter names are MyBatis-Plus internals.
        assertThat(query.getSqlSegment())
                .contains("conversation_id = #{")
                .contains("AND seq > #{")
                .endsWith(") LIMIT 51");
        assertThat(query.getParamNameValuePairs().values()).containsExactlyInAnyOrder(CONVERSATION_ID, 150);
    }

    @Test
    void theSliceShouldNeverSortInTheDatabaseTest() {
        when(eventMapper.selectList(any())).thenReturn(List.of());

        repository.findByConversationIdAfterSeq(CONVERSATION_ID, 0, 201);

        QueryWrapper<RmqAiEvent> query = capturedQuery();
        // See the class javadoc: MEDIUMTEXT payload + a sort the unique key cannot serve = the whole
        // value of every row copied into sort_buffer_size. The slice is sorted in Java instead, which
        // the next case asserts.
        assertThat(query.getTargetSql().toUpperCase(Locale.ROOT)).doesNotContain("ORDER BY");
        assertThat(query.getSqlSegment().toUpperCase(Locale.ROOT)).doesNotContain("ORDER BY");
        assertThat(query.getTargetSql()).isEqualTo(SLICE_SQL + 201);
    }

    @Test
    void theSliceShouldBeSortedInMemoryBySeqThenIdTest() {
        // Scrambled on purpose: the database was not asked to sort, so whatever order the storage engine
        // happens to return is what arrives here.
        when(eventMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(
                row(905L, 5), row(903L, 3), row(909L, 9), row(901L, 1))));

        List<RmqAiEvent> slice = repository.findByConversationIdAfterSeq(CONVERSATION_ID, 0, 50);

        assertThat(slice).extracting(RmqAiEvent::getSeq).containsExactly(1, 3, 5, 9);
        assertThat(capturedQuery().getTargetSql()).doesNotContain("ORDER BY");

        // uk_ai_event_conversation_seq makes equal seq values impossible in the database, so the id
        // tie-break can only fire on a fixture; it is what makes the comparator total, and therefore
        // what keeps two reads of the same slice from disagreeing about the order.
        when(eventMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(row(902L, 4), row(901L, 4))));

        assertThat(repository.findByConversationIdAfterSeq(CONVERSATION_ID, 0, 50))
                .extracting(RmqAiEvent::getId)
                .containsExactly(901L, 902L);
    }

    // --- the cursor the caller derives -------------------------------------------

    @Test
    void theCursorShouldBeTheLastRowOfThePageWithTheProbeRowDroppedTest() {
        AiConversationService service = serviceOverTheRealRepository();
        when(eventMapper.selectList(any())).thenReturn(rows(1, 201));

        AiConversationService.TimelinePage page = service.timeline(CONVERSATION_ID, OWNER, 0, 200);

        assertThat(capturedQuery().getTargetSql()).isEqualTo(SLICE_SQL + 201);
        assertThat(page.items()).hasSize(200);
        assertThat(page.items().get(0).seq()).isEqualTo(1);
        // nextAfter is the seq of the last row the caller keeps, not of the extra row that was only
        // fetched to prove there is more: a cursor pointing past the page would skip an event.
        assertThat(page.nextAfter()).isEqualTo(200);
        assertThat(page.items().get(199).seq()).isEqualTo(200);
    }

    @Test
    void aNonPositivePageShouldFallBackToTheDefaultLimitTest() {
        AiConversationService service = serviceOverTheRealRepository();
        when(eventMapper.selectList(any())).thenReturn(List.of());

        service.timeline(CONVERSATION_ID, OWNER, 0, 0);
        service.timeline(CONVERSATION_ID, OWNER, 0, -20);

        String expected = SLICE_SQL + (AiConversationService.DEFAULT_TIMELINE_LIMIT + 1);
        assertThat(capturedQueries()).hasSize(2)
                .allSatisfy(query -> assertThat(query.getTargetSql()).isEqualTo(expected));
    }

    @Test
    void aNegativeCursorShouldBeClampedToTheStartOfTheConversationTest() {
        AiConversationService service = serviceOverTheRealRepository();
        when(eventMapper.selectList(any())).thenReturn(List.of());

        service.timeline(CONVERSATION_ID, OWNER, -5, 20);

        QueryWrapper<RmqAiEvent> query = capturedQuery();
        assertThat(query.getTargetSql()).isEqualTo(SLICE_SQL + 21);
        // seq starts at 1, so a negative cursor from a hand-built URL must not become a `seq > -5`
        // predicate; it means "from the beginning". Read the bound values after getTargetSql():
        // MyBatis-Plus builds them lazily, as part of rendering the SQL.
        assertThat(query.getParamNameValuePairs().values()).containsExactlyInAnyOrder(CONVERSATION_ID, 0);
    }

    @Test
    void theLimitShouldStopAtTheCeilingTest() {
        when(eventMapper.selectList(any())).thenReturn(List.of());

        repository.findByConversationIdAfterSeq(CONVERSATION_ID, 0, 5000);

        assertThat(capturedQuery().getTargetSql()).isEqualTo(SLICE_SQL + 500);

        AiConversationService service = serviceOverTheRealRepository();
        service.timeline(CONVERSATION_ID, OWNER, 0, 5000);

        // The service caps at MAX_TIMELINE_LIMIT and then asks for one more, so at the ceiling the probe
        // row is what the repository's own cap eats. nextAfter is still the last row of a full page, and
        // a client stops when a page comes back empty, so nothing is lost by it.
        assertThat(capturedQueries()).hasSize(2)
                .allSatisfy(query -> assertThat(query.getTargetSql()).isEqualTo(SLICE_SQL + 500));
    }

    @Test
    void aSliceThatCannotReturnAnythingShouldNotReachTheDatabaseTest() {
        // The repository guards its own inputs: a caller that asks for nothing gets nothing back rather
        // than a query MySQL would still have to parse and plan.
        assertThat(repository.findByConversationIdAfterSeq(CONVERSATION_ID, 0, 0)).isEmpty();
        assertThat(repository.findByConversationIdAfterSeq(CONVERSATION_ID, 0, -1)).isEmpty();
        assertThat(repository.findByConversationIdAfterSeq(null, 0, 50)).isEmpty();

        verify(eventMapper, never()).selectList(any());
    }

    // --- maxSeq, the seed of the seq allocator --------------------------------------

    /**
     * {@code MAX(seq)} and not {@code rmq_ai_conversation.last_seq} is what a new run seeds its
     * allocator from: last_seq is a cache flushed after a write, so a run that died mid-flight leaves it
     * behind the truth and a seed taken from it would allocate a seq the unique key then rejects. The
     * projection has to stay an index-only read of {@code uk_ai_event_conversation_seq} — no ordering, no
     * second column — and has to answer 0 rather than null for a conversation with no events.
     */
    @Test
    void maxSeqShouldReadTheDatabaseHighWaterMarkTest() {
        when(eventMapper.<Object>selectObjs(any())).thenReturn(List.<Object>of(150L));

        assertThat(repository.maxSeq(CONVERSATION_ID)).isEqualTo(150);

        QueryWrapper<RmqAiEvent> query = capturedEventAggregate();
        assertThat(query.getSqlSelect()).contains("COALESCE(MAX(seq), 0)");
        assertThat(query.getTargetSql())
                .contains("conversation_id = ?")
                .doesNotContainIgnoringCase("order by");
        // Read the bound values after the SQL: MyBatis-Plus formats the parameters while rendering it.
        assertThat(query.getParamNameValuePairs().values()).containsExactly(CONVERSATION_ID);
    }

    @Test
    void maxSeqShouldBeZeroWhenTheAggregateComesBackWithoutAValueTest() {
        // COALESCE means the database answers 0 and never null, so these three shapes are about the
        // mapper and the driver rather than about the query. They still have to be covered: admission of
        // every run reads this value, so a null seed is not a null return but a run that cannot start.
        when(eventMapper.<Object>selectObjs(any())).thenReturn(null);
        assertThat(repository.maxSeq(CONVERSATION_ID)).isZero();

        when(eventMapper.<Object>selectObjs(any())).thenReturn(List.of());
        assertThat(repository.maxSeq(CONVERSATION_ID)).isZero();

        when(eventMapper.<Object>selectObjs(any())).thenReturn(Collections.singletonList(null));
        assertThat(repository.maxSeq(CONVERSATION_ID)).isZero();
    }

    @Test
    void maxSeqShouldNeedAConversationTest() {
        assertThat(repository.maxSeq(null)).isZero();

        verify(eventMapper, never()).selectObjs(any());
    }

    // --- the cascade both DELETE and the retention sweep rely on ---------------------

    /**
     * An empty id list must not reach the database: MyBatis-Plus renders {@code IN ()} for an empty
     * collection, which is a syntax error, and the retention sweep calls this on every pass that has
     * nothing to do. A guard that only lived in the sweep would leave the port free to emit invalid SQL
     * for its next caller.
     */
    @Test
    void anEmptyDeleteBatchShouldNotBuildAnEmptyInClauseTest() {
        assertThat(repository.deleteByConversationIds(List.of())).isZero();
        assertThat(repository.deleteByConversationIds(null)).isZero();
        assertThat(repository.deleteByConversationId(null)).isZero();
        assertThat(runs.deleteByConversationIds(List.of())).isZero();
        assertThat(runs.deleteByConversationIds(null)).isZero();
        assertThat(runs.deleteByConversationId(null)).isZero();
        assertThat(conversations.deleteByIds(List.of())).isZero();
        assertThat(conversations.deleteByIds(null)).isZero();
        assertThat(conversations.deleteById(null)).isZero();

        verify(eventMapper, never()).delete(any());
        verify(runMapper, never()).delete(any());
        verify(conversationMapper, never()).deleteByIds(any());
        verify(conversationMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void aDeleteBatchShouldGoOutAsOneStatementTest() {
        when(eventMapper.delete(any())).thenReturn(12);
        when(runMapper.delete(any())).thenReturn(4);

        assertThat(repository.deleteByConversationIds(List.of(7L, 8L))).isEqualTo(12);
        assertThat(runs.deleteByConversationIds(List.of(7L, 8L))).isEqualTo(4);

        assertThat(capturedEventDelete().getTargetSql()).contains("conversation_id IN (?,?)");
        assertThat(capturedRunDelete().getTargetSql()).contains("conversation_id IN (?,?)");
    }

    // --- the conversation list -------------------------------------------------------

    /**
     * Ordering by {@code gmt_modified} is a documented deviation from this project's usual
     * {@code gmt_create desc}, and {@code idx_ai_conversation_owner (owner, archived, gmt_modified, id)}
     * serves it only as written: two leading equalities, then the exact sort key, so a page comes back
     * with no filesort and no temporary table. Sorting by {@code gmt_create} instead would return the
     * same rows and silently lose the index, which is why the SQL text and not the result is the
     * assertion. A conversation used again today has to float to the top of the list.
     */
    @Test
    void theListShouldScopeToOneOwnerAndOrderByGmtModifiedDescTest() {
        stubEmptyPage();

        conversations.findPage(OWNER, null, null, 1, 20);

        QueryWrapper<RmqAiConversation> query = capturedConversationPage();
        assertThat(query.getTargetSql())
                .contains("owner = ?")
                .contains("ORDER BY gmt_modified DESC,id DESC")
                .doesNotContain("archived")
                .doesNotContain("title");
        assertThat(query.getParamNameValuePairs().values()).containsExactly(OWNER);
    }

    /**
     * {@code escapeLike} is a private copy of {@code QueryHistoryService.escapeLike} — the project has no
     * shared SqlLikeUtils — so the escaping is asserted rather than trusted. The backslash is replaced
     * first: escaping {@code %} and {@code _} first would double-escape the backslashes this method
     * inserts itself, and an unescaped term would let a user turn a title search into a full scan by
     * typing a single {@code %}.
     */
    @Test
    void theListShouldEscapeEveryLikeWildcardTest() {
        stubEmptyPage();

        conversations.findPage(OWNER, "a\\b%c_d", null, 1, 20);

        QueryWrapper<RmqAiConversation> query = capturedConversationPage();
        assertThat(query.getTargetSql()).contains("title LIKE ?");
        // In any order: MyBatis-Plus numbers the bound parameters as it renders them rather than as the
        // clauses read, and the point here is the escaping, not the numbering.
        assertThat(query.getParamNameValuePairs().values())
                .containsExactlyInAnyOrder(OWNER, "%a\\\\b\\%c\\_d%");
    }

    @Test
    void theListShouldDropBlankFiltersTest() {
        stubEmptyPage();

        conversations.findPage(OWNER, "   ", null, 1, 20);

        QueryWrapper<RmqAiConversation> query = capturedConversationPage();
        // A blank term must not become LIKE '%%': that matches every row of the owner's history and
        // defeats the index the ordering above depends on.
        assertThat(query.getTargetSql())
                .contains("owner = ?")
                .doesNotContain("LIKE")
                .doesNotContain("archived");
    }

    @Test
    void theListShouldFilterOnArchivedOnlyWhenAskedTest() {
        stubEmptyPage();

        conversations.findPage(OWNER, null, Boolean.TRUE, 1, 20);

        QueryWrapper<RmqAiConversation> query = capturedConversationPage();
        assertThat(query.getTargetSql()).contains("archived = ?");
        assertThat(query.getParamNameValuePairs().values()).contains(OWNER, true);
    }

    @Test
    void theListShouldMapTheDatabasePageIntoPageResultTest() {
        RmqAiConversation row = AiRunTestSupport.conversation(3L, OWNER);
        when(conversationMapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenReturn(new Page<RmqAiConversation>(2, 20).setRecords(List.of(row)).setTotal(41));

        PageResult<RmqAiConversation> page = conversations.findPage(OWNER, null, null, 2, 20);

        ArgumentCaptor<IPage<RmqAiConversation>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        verify(conversationMapper).selectPage(pageCaptor.capture(), any(Wrapper.class));
        // The database does the paging; PageResult only restates it, so both sides have to agree or the
        // drawer renders the wrong page number over the right rows.
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(2);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20);
        assertThat(page.getTotal()).isEqualTo(41);
        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(20);
        assertThat(page.getItems()).containsExactly(row);
    }

    /**
     * Empty rather than an exception, so {@code AiConversationService.requireOwned} answers 404 for
     * somebody else's id: a 403 would confirm the id exists, which is the enumeration guard this project
     * already applies to query history.
     */
    @Test
    void anOwnedLookupShouldFilterOnBothColumnsTest() {
        RmqAiConversation row = AiRunTestSupport.conversation(CONVERSATION_ID, OWNER);
        when(conversationMapper.selectOne(any())).thenReturn(row);

        assertThat(conversations.findByIdAndOwner(CONVERSATION_ID, OWNER)).contains(row);

        QueryWrapper<RmqAiConversation> query = capturedConversationSelectOne();
        assertThat(query.getTargetSql()).contains("id = ?").contains("owner = ?");
        assertThat(query.getParamNameValuePairs().values())
                .containsExactlyInAnyOrder(CONVERSATION_ID, OWNER);
    }

    @Test
    void anOwnedLookupShouldNeedAnIdAndAnOwnerTest() {
        assertThat(conversations.findByIdAndOwner(null, OWNER)).isEmpty();
        assertThat(conversations.findByIdAndOwner(CONVERSATION_ID, null)).isEmpty();
        assertThat(conversations.findByIdAndOwner(CONVERSATION_ID, "  ")).isEmpty();
        assertThat(conversations.findById(null)).isEmpty();

        verify(conversationMapper, never()).selectOne(any());
        verify(conversationMapper, never()).selectById(any());
    }

    @Test
    void theRetentionSweepShouldReadExpiredIdsOldestFirstTest() {
        // Integers on purpose: a driver may hand back any Number for a bigint column and the repository
        // widens through Number instead of casting to Long.
        when(conversationMapper.<Object>selectObjs(any())).thenReturn(List.<Object>of(11, 12));

        assertThat(conversations.findIdsCreatedBefore(LocalDateTime.of(2026, 9, 1, 0, 0), 5))
                .containsExactly(11L, 12L);

        QueryWrapper<RmqAiConversation> query = capturedConversationAggregate();
        // Only ids, oldest first, bounded: the sweep selects a batch of keys and deletes by key so one
        // pass cannot hold a whole table's worth of rows, and the ordering matches idx_*_cleanup.
        assertThat(query.getSqlSelect()).contains("id");
        assertThat(query.getTargetSql())
                .contains("gmt_create < ?")
                .contains("ORDER BY gmt_create ASC,id ASC")
                .contains("LIMIT 5");
    }

    @Test
    void theRetentionSweepShouldNotQueryWithoutACutoffOrABatchTest() {
        assertThat(conversations.findIdsCreatedBefore(null, 5)).isEmpty();
        assertThat(conversations.findIdsCreatedBefore(LocalDateTime.now(), 0)).isEmpty();
        assertThat(conversations.findIdsCreatedBefore(LocalDateTime.now(), -1)).isEmpty();

        verify(conversationMapper, never()).selectObjs(any());
    }

    /**
     * Forgetting the remembered {@code --resume} session is the recovery for a stale one, so this write
     * has to actually null the column. {@code updateById} skips null fields, which is why the port has a
     * method of its own — a fake pass through the ordinary update would be a silent no-op that leaves the
     * conversation pointing at a session that no longer exists.
     */
    @Test
    void forgettingTheRuntimeSessionShouldNullTheColumnByNameTest() {
        conversations.clearRuntimeSessionId(CONVERSATION_ID);

        UpdateWrapper<RmqAiConversation> update = capturedConversationUpdate();
        assertThat(update.getSqlSet()).contains("runtime_session_id");
        // The bound value is what makes it a clear rather than a no-op.
        assertThat(update.getParamNameValuePairs()).containsValue(null);
        assertThat(update.getTargetSql()).contains("id = ?");
        assertThat(update.getParamNameValuePairs().values()).contains(CONVERSATION_ID);
    }

    @Test
    void forgettingTheRuntimeSessionShouldNeedAnIdTest() {
        conversations.clearRuntimeSessionId(null);

        verify(conversationMapper, never()).update(any(), any());
    }

    // --- the run rows -----------------------------------------------------------------

    /**
     * The admission guard ("this conversation already has a run in flight"), the startup reaper and the
     * orphan sweep all read {@link RunStatus#ACTIVE_STATUSES}. A second, hard-coded
     * {@code ("QUEUED","RUNNING")} list here is how a future non-terminal status gets admitted twice —
     * two writers, one seq allocator — so the vocabulary is asserted as well as the query that uses it.
     */
    @Test
    void theActiveRunLookupShouldUseTheSharedStatusVocabularyTest() {
        RmqAiRun active = AiRunTestSupport.run(41L, CONVERSATION_ID, 2, RunStatus.RUNNING);
        when(runMapper.selectList(any())).thenReturn(List.of(active));

        assertThat(runs.findActiveByConversationId(CONVERSATION_ID)).contains(active);

        QueryWrapper<RmqAiRun> query = capturedRunSelectList();
        assertThat(query.getTargetSql())
                .contains("conversation_id = ?")
                .contains("status IN (?,?)")
                .contains("ORDER BY id DESC")
                .contains("LIMIT 1");
        assertThat(query.getParamNameValuePairs().values())
                .contains(RunStatus.QUEUED.name(), RunStatus.RUNNING.name());
        assertThat(RunStatus.ACTIVE_STATUSES)
                .as("the vocabulary this query is built from")
                .containsExactly(RunStatus.QUEUED.name(), RunStatus.RUNNING.name());
    }

    @Test
    void theActiveRunLookupShouldNeedAConversationTest() {
        assertThat(runs.findActiveByConversationId(null)).isEmpty();

        verify(runMapper, never()).selectList(any());
    }

    /**
     * Turn numbers come from {@code MAX(turn) + 1}, so a null here is not a null return but a second
     * turn 1 — which {@code uk_ai_run_conversation_turn} then rejects, loudly, on the wrong statement.
     */
    @Test
    void maxTurnShouldBeTheHighestExistingTurnTest() {
        when(runMapper.<Object>selectObjs(any())).thenReturn(List.<Object>of(3L));

        assertThat(runs.maxTurn(CONVERSATION_ID)).isEqualTo(3);

        QueryWrapper<RmqAiRun> query = capturedRunAggregate();
        assertThat(query.getSqlSelect()).contains("COALESCE(MAX(turn), 0)");
        assertThat(query.getTargetSql())
                .contains("conversation_id = ?")
                .doesNotContainIgnoringCase("order by");
    }

    @Test
    void maxTurnShouldBeZeroWhenTheConversationNeverRanTest() {
        when(runMapper.<Object>selectObjs(any())).thenReturn(null);
        assertThat(runs.maxTurn(CONVERSATION_ID)).isZero();

        when(runMapper.<Object>selectObjs(any())).thenReturn(List.of());
        assertThat(runs.maxTurn(CONVERSATION_ID)).isZero();

        when(runMapper.<Object>selectObjs(any())).thenReturn(Collections.singletonList(null));
        assertThat(runs.maxTurn(CONVERSATION_ID)).isZero();

        assertThat(runs.maxTurn(null)).isZero();
    }

    /**
     * {@code gmt_modified} and not {@code started_at}: {@code idx_ai_run_active (status, gmt_modified)}
     * is what makes the sweep cheap, and a run that is still being written to keeps pushing the column
     * forward, so only a run nothing has touched for the whole orphan timeout is a candidate.
     */
    @Test
    void theStaleRunSweepShouldFilterNonTerminalRunsByGmtModifiedTest() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 1, 12, 0);
        RmqAiRun stale = AiRunTestSupport.run(41L, CONVERSATION_ID, 1, RunStatus.RUNNING);
        when(runMapper.selectList(any())).thenReturn(List.of(stale));

        assertThat(runs.findStaleActive(cutoff, RunStatus.ACTIVE_STATUSES)).containsExactly(stale);

        QueryWrapper<RmqAiRun> query = capturedRunSelectList();
        assertThat(query.getTargetSql()).contains("status IN (?,?)").contains("gmt_modified < ?");
        assertThat(query.getParamNameValuePairs().values()).contains(cutoff);
    }

    @Test
    void theStaleRunSweepShouldNotQueryWithoutACutoffOrAVocabularyTest() {
        assertThat(runs.findStaleActive(null, RunStatus.ACTIVE_STATUSES)).isEmpty();
        assertThat(runs.findStaleActive(LocalDateTime.now(), List.of())).isEmpty();
        assertThat(runs.findStaleActive(LocalDateTime.now(), null)).isEmpty();
        assertThat(runs.findByStatusIn(List.of())).isEmpty();
        assertThat(runs.findByStatusIn(null)).isEmpty();

        verify(runMapper, never()).selectList(any());
    }

    @Test
    void theRunHistoryShouldOrderByTurnTest() {
        when(runMapper.selectList(any())).thenReturn(List.of());

        runs.findByConversationId(CONVERSATION_ID);

        assertThat(capturedRunSelectList().getTargetSql())
                .contains("conversation_id = ?")
                .contains("ORDER BY turn ASC,id ASC");
    }

    /**
     * Two statements for a whole page instead of one per conversation: {@code MAX(id)} grouped by
     * conversation is an index-only lookup on {@code idx_ai_run_conversation (conversation_id, id)}, then
     * one {@code IN} for those rows. The list endpoint shows {@code lastRunId}/{@code lastRunStatus} on
     * every row, so the per-row version would be a hundred round trips for a hundred-row page.
     */
    @Test
    void theLastRunOfAPageShouldCostTwoQueriesTest() {
        when(runMapper.<Object>selectObjs(any())).thenReturn(List.<Object>of(101, 102));
        when(runMapper.selectList(any())).thenReturn(List.of(
                AiRunTestSupport.run(101L, 7L, 1, RunStatus.COMPLETED),
                AiRunTestSupport.run(102L, 8L, 4, RunStatus.STOPPED)));

        Map<Long, RmqAiRun> latest = runs.findLatestByConversationIds(Arrays.asList(7L, 8L, 8L, null));

        assertThat(latest).containsOnlyKeys(7L, 8L);
        assertThat(latest.get(8L).getTurn()).isEqualTo(4);

        QueryWrapper<RmqAiRun> grouped = capturedRunAggregate();
        assertThat(grouped.getSqlSelect()).contains("MAX(id)");
        assertThat(grouped.getTargetSql())
                .contains("conversation_id IN (?,?)")
                .contains("GROUP BY conversation_id");
        assertThat(capturedRunSelectList().getTargetSql()).contains("id IN (?,?)");
    }

    /**
     * {@code MAX(id)} already yields one row per conversation, so the merge is only a tie-break — but
     * without it an unexpected duplicate would report whichever row the driver happened to return first,
     * possibly the older run, as the conversation's last one.
     */
    @Test
    void theLastRunOfAConversationShouldWinTheTieBreakTest() {
        RmqAiRun detached = AiRunTestSupport.run(103L, 8L, 9, RunStatus.COMPLETED);
        detached.setConversationId(null);
        when(runMapper.<Object>selectObjs(any())).thenReturn(List.<Object>of(102));
        when(runMapper.selectList(any())).thenReturn(List.of(
                AiRunTestSupport.run(100L, 8L, 3, RunStatus.COMPLETED),
                AiRunTestSupport.run(102L, 8L, 4, RunStatus.STOPPED),
                detached));

        Map<Long, RmqAiRun> latest = runs.findLatestByConversationIds(List.of(8L));

        assertThat(latest).containsOnlyKeys(8L);
        assertThat(latest.get(8L).getId()).isEqualTo(102L);
    }

    @Test
    void theLastRunLookupShouldNotQueryWithoutConversationsTest() {
        assertThat(runs.findLatestByConversationIds(null)).isEmpty();
        assertThat(runs.findLatestByConversationIds(List.of())).isEmpty();
        assertThat(runs.findLatestByConversationIds(Collections.singletonList(null))).isEmpty();

        verify(runMapper, never()).selectObjs(any());
        verify(runMapper, never()).selectList(any());
    }

    @Test
    void theLastRunLookupShouldSkipTheRowQueryWhenNoConversationEverRanTest() {
        when(runMapper.<Object>selectObjs(any())).thenReturn(Collections.singletonList(null));

        assertThat(runs.findLatestByConversationIds(List.of(CONVERSATION_ID))).isEmpty();

        verify(runMapper, never()).selectList(any());
    }

    // --- harness ------------------------------------------------------------------

    /**
     * The real service over the real repository over a mocked mapper: the cursor arithmetic lives in the
     * service and the SQL lives in the repository, and the point of these cases is that the two agree on
     * how many rows to ask for.
     */
    private AiConversationService serviceOverTheRealRepository() {
        RmqAiConversation stored = AiRunTestSupport.conversation(CONVERSATION_ID, OWNER);
        when(conversationPort.findByIdAndOwner(CONVERSATION_ID, OWNER)).thenReturn(Optional.of(stored));
        when(runPort.findActiveByConversationId(CONVERSATION_ID)).thenReturn(Optional.empty());
        return new AiConversationService(conversationPort, runPort, repository,
                new AgentRunRegistry(), mock(RmqctlWorkspace.class), mock(LlmConfigService.class),
                new AiConversationProperties(), JSON, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private QueryWrapper<RmqAiEvent> capturedQuery() {
        List<QueryWrapper<RmqAiEvent>> queries = capturedQueries();
        return queries.get(queries.size() - 1);
    }

    private List<QueryWrapper<RmqAiEvent>> capturedQueries() {
        ArgumentCaptor<Wrapper<RmqAiEvent>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(eventMapper, atLeastOnce()).selectList(captor.capture());
        List<QueryWrapper<RmqAiEvent>> queries = new ArrayList<>();
        for (Wrapper<RmqAiEvent> wrapper : captor.getAllValues()) {
            queries.add((QueryWrapper<RmqAiEvent>) wrapper);
        }
        return queries;
    }

    /** The list query returns nothing: these cases assert the SQL, not the rows. */
    private void stubEmptyPage() {
        when(conversationMapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenReturn(new Page<RmqAiConversation>(1, 20).setRecords(List.of()).setTotal(0));
    }

    private QueryWrapper<RmqAiEvent> capturedEventAggregate() {
        ArgumentCaptor<Wrapper<RmqAiEvent>> captor = wrapperCaptor();
        verify(eventMapper).selectObjs(captor.capture());
        return queryOf(captor.getValue());
    }

    private QueryWrapper<RmqAiEvent> capturedEventDelete() {
        ArgumentCaptor<Wrapper<RmqAiEvent>> captor = wrapperCaptor();
        verify(eventMapper).delete(captor.capture());
        return queryOf(captor.getValue());
    }

    private QueryWrapper<RmqAiConversation> capturedConversationPage() {
        ArgumentCaptor<Wrapper<RmqAiConversation>> captor = wrapperCaptor();
        verify(conversationMapper).selectPage(any(IPage.class), captor.capture());
        return queryOf(captor.getValue());
    }

    private QueryWrapper<RmqAiConversation> capturedConversationSelectOne() {
        ArgumentCaptor<Wrapper<RmqAiConversation>> captor = wrapperCaptor();
        verify(conversationMapper).selectOne(captor.capture());
        return queryOf(captor.getValue());
    }

    private QueryWrapper<RmqAiConversation> capturedConversationAggregate() {
        ArgumentCaptor<Wrapper<RmqAiConversation>> captor = wrapperCaptor();
        verify(conversationMapper).selectObjs(captor.capture());
        return queryOf(captor.getValue());
    }

    @SuppressWarnings("unchecked")
    private UpdateWrapper<RmqAiConversation> capturedConversationUpdate() {
        ArgumentCaptor<Wrapper<RmqAiConversation>> captor = wrapperCaptor();
        verify(conversationMapper).update(isNull(), captor.capture());
        return (UpdateWrapper<RmqAiConversation>) captor.getValue();
    }

    private QueryWrapper<RmqAiRun> capturedRunSelectList() {
        ArgumentCaptor<Wrapper<RmqAiRun>> captor = wrapperCaptor();
        verify(runMapper).selectList(captor.capture());
        return queryOf(captor.getValue());
    }

    private QueryWrapper<RmqAiRun> capturedRunAggregate() {
        ArgumentCaptor<Wrapper<RmqAiRun>> captor = wrapperCaptor();
        verify(runMapper).selectObjs(captor.capture());
        return queryOf(captor.getValue());
    }

    private QueryWrapper<RmqAiRun> capturedRunDelete() {
        ArgumentCaptor<Wrapper<RmqAiRun>> captor = wrapperCaptor();
        verify(runMapper).delete(captor.capture());
        return queryOf(captor.getValue());
    }

    @SuppressWarnings("unchecked")
    private static <T> ArgumentCaptor<Wrapper<T>> wrapperCaptor() {
        return ArgumentCaptor.forClass(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> QueryWrapper<T> queryOf(Wrapper<T> wrapper) {
        assertThat(wrapper).isInstanceOf(QueryWrapper.class);
        return (QueryWrapper<T>) wrapper;
    }

    /** Rows {@code from}..{@code toInclusive}, ascending, each decodable as a text event. */
    private static List<RmqAiEvent> rows(int from, int toInclusive) {
        List<RmqAiEvent> rows = new ArrayList<>();
        for (int seq = from; seq <= toInclusive; seq++) {
            rows.add(row(900L + seq, seq));
        }
        return rows;
    }

    /**
     * One row with a payload written through {@link AiEventCodec}, the same path the sink uses, so a
     * fixture cannot drift from the shape the service decodes. The id is deliberately not the seq.
     */
    private static RmqAiEvent row(long id, int seq) {
        AiEventCodec.Payload payload = AiEventCodec
                .write(JSON, RUN_ID, new TimelineEvent.Text("row " + seq))
                .orElseThrow(() -> new IllegalStateException("a text event must serialise"));
        RmqAiEvent row = new RmqAiEvent();
        row.setId(id);
        row.setConversationId(CONVERSATION_ID);
        row.setRunId(RUN_ID);
        row.setTurn(1);
        row.setSeq(seq);
        row.setType(payload.type());
        row.setPayload(payload.json());
        return row;
    }
}
