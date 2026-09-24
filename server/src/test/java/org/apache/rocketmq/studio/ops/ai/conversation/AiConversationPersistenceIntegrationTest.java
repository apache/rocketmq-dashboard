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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;
import org.apache.rocketmq.studio.ops.ai.conversation.event.ThinkingSource;
import org.apache.rocketmq.studio.ops.ai.conversation.event.TimelineEvent;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiConversationListItemVO;
import org.apache.rocketmq.studio.persistence.entity.RmqAiConversation;
import org.apache.rocketmq.studio.persistence.entity.RmqAiEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiRun;
import org.apache.rocketmq.studio.persistence.mapper.RmqAiConversationMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqAiEventMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqAiRunMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The three AI conversation tables, read and written for real.
 *
 * <p>Runs on the {@code dev} profile, i.e. on H2 in MySQL compatibility mode with
 * {@code classpath:db/schema.sql} as the DDL. That is a deliberate improvement on the existing
 * {@code @SpringBootTest}s in this repository: {@code QueryHistoryServiceIntegrationTest} and
 * {@code RmqAlertStateMapperIntegrationTest} take no profile, so they resolve to
 * {@code jdbc:mysql://localhost:3306/rocketmq} and the {@code backend-test} CI job — which has no MySQL
 * service — has never run them. This one does run there, and starting the context at all is the proof
 * that the new DDL parses under H2, which matters because the dev profile feeds that same file to H2 on
 * every local startup.
 *
 * <p>What only a real database can tell us, and what these cases therefore assert:
 * <ul>
 *   <li>the column mapping survives a round trip — {@code TINYINT(1)} to {@code Boolean},
 *       {@code MEDIUMTEXT} to the polymorphic {@link TimelineEvent} payload, {@code DATETIME} to
 *       {@link LocalDateTime}, and the generated keys back onto the entities;</li>
 *   <li>the aggregate and grouping queries the repositories hand-write actually run:
 *       {@code COALESCE(MAX(seq), 0)}, {@code MAX(id) ... GROUP BY conversation_id}, the paged list with
 *       its {@code gmt_modified desc, id desc} ordering, and the LIKE escaping of a search term;</li>
 *   <li>owner scoping is enforced by the query and not only by the service, so somebody else's id really
 *       is a 404;</li>
 *   <li>the retention sweep deletes children before parents. This project declares no foreign keys, so
 *       that order is the whole cascade: the reverse would leave events nobody can find any more.</li>
 * </ul>
 *
 * <p>The repositories are spies rather than mocks: every call still reaches H2, and the spy is what makes
 * the delete order observable at all, since with no FK constraints the database would happily accept any
 * order.
 */
@SpringBootTest
@ActiveProfiles("dev")
class AiConversationPersistenceIntegrationTest {

    private static final String OWNER = "ai-persistence-it";
    private static final String FOREIGN_OWNER = "ai-persistence-it-other";
    private static final String INSTANCE_ID = "open-source-local";

    /** Two runs of a hundred events each: enough that a 200-row page and a 50-row tail are both real. */
    private static final int EVENTS_PER_RUN = 100;
    private static final int EVENT_COUNT = EVENTS_PER_RUN * 2;

    private static final LocalDateTime CREATED = LocalDateTime.now().minusDays(2).withNano(0);

    @MockitoSpyBean
    private AiConversationRepository conversationRepository;

    @MockitoSpyBean
    private AiRunRepository runRepository;

    @MockitoSpyBean
    private AiEventRepository eventRepository;

    @Autowired
    private AiConversationService conversationService;

    @Autowired
    private AiConversationProperties properties;

    @Autowired
    private RmqAiConversationMapper conversationMapper;

    @Autowired
    private RmqAiRunMapper runMapper;

    @Autowired
    private RmqAiEventMapper eventMapper;

    @Autowired
    private ObjectMapper objectMapper;

    /** Everything this class inserted, so a failure cannot leak rows into the next test. */
    private final List<Long> seededConversations = new ArrayList<>();

    private int originalRetentionDays;

    @BeforeEach
    void rememberRetention() {
        originalRetentionDays = properties.getRetentionDays();
    }

    @AfterEach
    void purgeSeededRowsAndRestoreProperties() {
        properties.setRetentionDays(originalRetentionDays);
        if (!seededConversations.isEmpty()) {
            eventMapper.delete(new QueryWrapper<RmqAiEvent>().in("conversation_id", seededConversations));
            runMapper.delete(new QueryWrapper<RmqAiRun>().in("conversation_id", seededConversations));
            conversationMapper.deleteByIds(seededConversations);
            seededConversations.clear();
        }
    }

    @Test
    void aWholeConversationShouldSurviveTheRoundTripThroughTheSchemaTest() {
        Seeded seeded = seed();

        // Generated keys come back onto the entities; nothing downstream works without them.
        assertThat(seeded.conversationId).isPositive();
        assertThat(seeded.firstRunId).isPositive();
        assertThat(seeded.secondRunId).isGreaterThan(seeded.firstRunId);

        // The hand-written aggregates run for real: COALESCE(MAX(seq), 0) and MAX(turn).
        assertThat(eventRepository.maxSeq(seeded.conversationId)).isEqualTo(EVENT_COUNT);
        assertThat(runRepository.maxTurn(seeded.conversationId)).isEqualTo(2);

        RmqAiConversation stored = conversationRepository.findById(seeded.conversationId).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("why is my consumer lagging");
        assertThat(stored.getOwner()).isEqualTo(OWNER);
        assertThat(stored.getInstanceId()).isEqualTo(INSTANCE_ID);
        assertThat(stored.getMode()).isEqualTo("diagnose");
        assertThat(stored.getGmtCreate()).isEqualTo(CREATED);
        // TINYINT(1) has to come back as a Boolean and not as a number the UI would render as "0".
        assertThat(stored.getArchived()).isFalse();

        PageResult<RmqAiConversation> page = conversationService.list(OWNER, null, null, 1, 10);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getItems()).extracting(RmqAiConversation::getId).containsExactly(seeded.conversationId);
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(10);

        // The list rows carry their newest run, resolved by one MAX(id) GROUP BY query for the page.
        PageResult<AiConversationListItemVO> items = conversationService.listItems(OWNER, null, null, 1, 10);
        assertThat(items.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(seeded.conversationId);
            assertThat(item.getLastRunId()).isEqualTo(seeded.secondRunId);
            assertThat(item.getLastRunStatus()).isEqualTo(RunStatus.COMPLETED);
        });
    }

    @Test
    void theListShouldSearchByTitleAndEscapeItsOwnWildcardsTest() {
        Seeded seeded = seed();
        long foreign = seedConversation(FOREIGN_OWNER, "why is my consumer lagging");

        assertThat(conversationService.list(OWNER, "lagging", null, 1, 10).getItems())
                .extracting(RmqAiConversation::getId)
                .containsExactly(seeded.conversationId);
        assertThat(conversationService.list(OWNER, "no such conversation", null, 1, 10).getTotal()).isZero();

        // A bare % must match nothing rather than everything: the term is escaped before it reaches the
        // QueryWrapper, and this is the only place that can prove it against a real LIKE.
        assertThat(conversationService.list(OWNER, "%", null, 1, 10).getTotal()).isZero();
        assertThat(conversationService.list(OWNER, "_", null, 1, 10).getTotal()).isZero();

        // Owner scoping is in the WHERE clause, so another operator's conversation is invisible here and
        // a direct lookup of its id answers 404 — not 403, which would confirm the id exists.
        assertThat(conversationService.list(OWNER, null, null, 1, 10).getTotal()).isEqualTo(1);
        assertThatThrownBy(() -> conversationService.requireOwned(foreign, OWNER))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(404));
    }

    /**
     * The escape clause has two jobs, and the bare-wildcard case above only proves the first: a term
     * that is nothing but {@code %} must not match everything. It must also still match a title that
     * really contains a {@code %} or an {@code _} — that is what "escaped as literal text" means — and
     * a term that merely looks like a pattern must not match in its place.
     *
     * <p>This case goes through {@code selectPage}, i.e. through the MyBatis-Plus pagination
     * interceptor, so the {@code COUNT} query the interceptor derives runs the same predicate: an
     * {@code ESCAPE} clause its parser could not carry into the count SQL would fail here rather than
     * on a MySQL deployment.
     */
    @Test
    void theSearchShouldMatchALiteralPercentOrUnderscoreInTheTitleTest() {
        long literal = seedConversation(OWNER, "progress 100%_done");
        seedConversation(OWNER, "progress 100xydone");

        assertThat(conversationService.list(OWNER, "100%_done", null, 1, 10).getItems())
                .extracting(RmqAiConversation::getId)
                .containsExactly(literal);
        assertThat(conversationService.list(OWNER, "100xydone", null, 1, 10).getItems())
                .extracting(RmqAiConversation::getId)
                .hasSize(1)
                .doesNotContain(literal);
    }

    /**
     * Ordering, paging and owner scoping against a real database, because all three are properties of the
     * SQL and of {@code idx_ai_conversation_owner} rather than of the Java that calls them.
     *
     * <p>Every conversation here is seeded with the same {@code gmt_create} and {@code gmt_modified}, so
     * the first assertion can only pass on the index's {@code id desc} tie-break; the bump below then
     * proves the ordering really keys on {@code gmt_modified}. A conversation used again today has to
     * float above one created later but untouched since — that is the whole reason this list deviates
     * from the project-wide {@code gmt_create desc}, and a real database is the only place the deviation
     * can be shown to survive the index it was designed around.
     */
    @Test
    void theListShouldPageByGmtModifiedDescAndStayScopedToItsOwnerTest() {
        long usedAgain = seedConversation(OWNER, "used again conversation");
        long middle = seedConversation(OWNER, "middle conversation");
        long untouched = seedConversation(OWNER, "untouched conversation");
        long foreign = seedConversation(FOREIGN_OWNER, "used again conversation");

        // All three of the owner's rows share gmt_modified, so only the id tie-break can decide here.
        assertThat(conversationService.list(OWNER, null, null, 1, 10).getItems())
                .extracting(RmqAiConversation::getId)
                .containsExactly(untouched, middle, usedAgain);

        // The lowest-id conversation is used again and must jump to the top of the list.
        touch(usedAgain, CREATED.plusMinutes(30));

        PageResult<RmqAiConversation> firstPage = conversationService.list(OWNER, null, null, 1, 2);
        assertThat(firstPage.getTotal()).isEqualTo(3);
        assertThat(firstPage.getPage()).isEqualTo(1);
        assertThat(firstPage.getItems()).extracting(RmqAiConversation::getId)
                .containsExactly(usedAgain, untouched);

        PageResult<RmqAiConversation> secondPage = conversationService.list(OWNER, null, null, 2, 2);
        assertThat(secondPage.getTotal()).isEqualTo(3);
        assertThat(secondPage.getPage()).isEqualTo(2);
        assertThat(secondPage.getItems()).extracting(RmqAiConversation::getId).containsExactly(middle);

        // The foreign row carries the same title as the newest of the owner's and sits inside the same
        // window, so a leaked owner filter would arrive as a fourth row rather than as an obvious error.
        assertThat(conversationService.list(OWNER, null, null, 1, 10).getTotal()).isEqualTo(3);
        assertThat(conversationService.list(FOREIGN_OWNER, "used again", null, 1, 10).getItems())
                .extracting(RmqAiConversation::getId)
                .containsExactly(foreign);
    }

    @Test
    void theTimelineShouldReplayFromAnyCursorTest() {
        Seeded seeded = seed();

        AiConversationService.TimelinePage tail =
                conversationService.timeline(seeded.conversationId, OWNER, 150, 200);

        assertThat(tail.items()).hasSize(EVENT_COUNT - 150);
        assertThat(tail.items()).extracting(AiConversationService.TimelineItem::seq)
                .containsExactlyElementsOf(expectedSeqs(151, EVENT_COUNT));
        assertThat(tail.nextAfter()).isEqualTo(EVENT_COUNT);
        // The payload column is MEDIUMTEXT and the rows are decoded polymorphically; both the text and
        // the denormalised turn/run columns have to come back intact for the UI to replay a finished run.
        AiConversationService.TimelineItem first = tail.items().get(0);
        assertThat(first.seq()).isEqualTo(151);
        assertThat(first.turn()).isEqualTo(2);
        assertThat(first.runId()).isEqualTo(seeded.secondRunId);
        assertThat(first.createdAt()).isNotNull();
        assertThat(first.event()).isEqualTo(new TimelineEvent.Text("answer 151"));

        AiConversationService.TimelinePage head =
                conversationService.timeline(seeded.conversationId, OWNER, 0, 20);

        assertThat(head.items()).extracting(AiConversationService.TimelineItem::seq)
                .containsExactlyElementsOf(expectedSeqs(1, 20));
        assertThat(head.nextAfter()).isEqualTo(20);
        // One of every stored shape, in the order the fixture writes them: the enhanced prompt of the
        // user turn is @JsonInclude(NON_NULL), so it survives only if the row really round-tripped.
        assertThat(head.items().get(0).event()).isEqualTo(
                new TimelineEvent.User("why is turn 1 slow", "rewritten: why is turn 1 slow"));
        assertThat(head.items().get(1).event()).isEqualTo(
                new TimelineEvent.Thinking("reading the offsets of turn 1", ThinkingSource.MODEL));
        assertThat(head.items().get(2).event()).isEqualTo(new TimelineEvent.ToolUse(
                "tc-1", "rmq.group.detail", Map.of("groupName", "GID_1")));
        assertThat(head.items().get(3).event()).isEqualTo(new TimelineEvent.ToolResult(
                "tc-1", "rmq.group.detail", "lag 4711", 8, false, true, 12L, null));
        assertThat(head.items().get(4).event()).isEqualTo(new TimelineEvent.Text("answer 5"));

        // Past the last seq there is nothing left and no cursor to hand back.
        AiConversationService.TimelinePage exhausted =
                conversationService.timeline(seeded.conversationId, OWNER, EVENT_COUNT, 200);
        assertThat(exhausted.items()).isEmpty();
        assertThat(exhausted.nextAfter()).isNull();
        assertThat(exhausted.activeRun()).isNull();

        // A run that is not terminal is reported next to the page, which is how a reload re-attaches to
        // a stream instead of showing a transcript that has silently stopped.
        markRunning(seeded.secondRunId);
        assertThat(conversationService.timeline(seeded.conversationId, OWNER, 0, 20).activeRun())
                .isNotNull()
                .satisfies(run -> {
                    assertThat(run.getId()).isEqualTo(seeded.secondRunId);
                    assertThat(run.getStatus()).isEqualTo(RunStatus.RUNNING.name());
                });
        markCompleted(seeded.secondRunId);
    }

    @Test
    void deletingAConversationShouldTakeItsChildrenWithItTest() {
        Seeded seeded = seed();

        conversationService.delete(seeded.conversationId, OWNER);

        // No foreign keys in this schema, so the service is the cascade. Anything left behind here is a
        // leak nothing else will ever find, because the parent that pointed at it is gone.
        assertThat(countEvents(seeded.conversationId)).isZero();
        assertThat(countRuns(seeded.conversationId)).isZero();
        assertThat(conversationMapper.selectById(seeded.conversationId)).isNull();
    }

    @Test
    void theRetentionSweepShouldPurgeChildrenBeforeParentsTest() {
        Seeded seeded = seed();
        assertThat(countEvents(seeded.conversationId)).isEqualTo(EVENT_COUNT);

        // A non-positive retention is "disabled", not "retain nothing" (see AiConversationProperties),
        // so this pass must leave the rows alone. The purge below therefore uses one day against rows
        // created two days ago, which is the honest equivalent of "everything has expired".
        properties.setRetentionDays(0);
        clearInvocations(conversationRepository, runRepository, eventRepository);

        conversationService.purgeExpired();

        assertThat(countEvents(seeded.conversationId)).isEqualTo(EVENT_COUNT);
        assertThat(countRuns(seeded.conversationId)).isEqualTo(2);
        assertThat(conversationMapper.selectById(seeded.conversationId)).isNotNull();

        properties.setRetentionDays(1);
        clearInvocations(conversationRepository, runRepository, eventRepository);

        conversationService.purgeExpired();

        // Events, then runs, then conversations. A crash halfway through this order leaves a parent
        // with no children, which the next pass finishes; the reverse order leaves children whose
        // parent is gone and which no query will ever reach again.
        InOrder order = inOrder(eventRepository, runRepository, conversationRepository);
        order.verify(eventRepository).deleteByConversationIds(List.of(seeded.conversationId));
        order.verify(runRepository).deleteByConversationIds(List.of(seeded.conversationId));
        order.verify(conversationRepository).deleteByIds(List.of(seeded.conversationId));

        assertThat(countEvents(seeded.conversationId)).isZero();
        assertThat(countRuns(seeded.conversationId)).isZero();
        assertThat(conversationMapper.selectById(seeded.conversationId)).isNull();
    }

    @Test
    void retentionShouldNotTouchAConversationInsideItsRetentionWindowTest() {
        Seeded seeded = seed();
        conversationMapper.updateById(recentCopyOf(seeded.conversationId));

        properties.setRetentionDays(1);
        clearInvocations(conversationRepository, runRepository, eventRepository);

        conversationService.purgeExpired();

        // The cutoff is on gmt_create, so a conversation created today survives a one-day retention.
        assertThat(conversationMapper.selectById(seeded.conversationId)).isNotNull();
        assertThat(countEvents(seeded.conversationId)).isEqualTo(EVENT_COUNT);
        verifyNoCascade();
    }

    // --- harness ------------------------------------------------------------------

    private void verifyNoCascade() {
        verify(eventRepository, never()).deleteByConversationIds(anyList());
        verify(runRepository, never()).deleteByConversationIds(anyList());
        verify(conversationRepository, never()).deleteByIds(anyList());
    }

    /** One conversation, two completed runs, {@value #EVENT_COUNT} events. */
    private Seeded seed() {
        long conversationId = seedConversation(OWNER, "why is my consumer lagging");
        long firstRunId = seedRun(conversationId, 1, 1, EVENTS_PER_RUN);
        long secondRunId = seedRun(conversationId, 2, EVENTS_PER_RUN + 1, EVENT_COUNT);
        seedEvents(conversationId, firstRunId, secondRunId);
        return new Seeded(conversationId, firstRunId, secondRunId);
    }

    private long seedConversation(String owner, String title) {
        RmqAiConversation conversation = new RmqAiConversation();
        conversation.setTitle(title);
        conversation.setOwner(owner);
        conversation.setEngine("claude-code");
        conversation.setModel("qwen3.8-max");
        conversation.setMode("diagnose");
        conversation.setInstanceId(INSTANCE_ID);
        conversation.setLastSeq(0);
        conversation.setArchived(false);
        conversation.setGmtCreate(CREATED);
        conversation.setGmtModified(CREATED);
        seededConversations.add(conversationRepository.insert(conversation).getId());
        return conversation.getId();
    }

    private long seedRun(long conversationId, int turn, int startSeq, int endSeq) {
        RmqAiRun run = new RmqAiRun();
        run.setConversationId(conversationId);
        run.setTurn(turn);
        run.setStatus(RunStatus.COMPLETED.name());
        run.setEngine("claude-code");
        run.setModel("qwen3.8-max");
        run.setRuntimeSessionId("4e943b51-9e53-42d9-b2e1-0cb2cc839ac4");
        run.setStartedAt(CREATED);
        run.setFinishedAt(CREATED.plusMinutes(1));
        run.setDurationMs(60_000L);
        run.setInputTokens(1_487);
        run.setOutputTokens(485);
        run.setStartSeq(startSeq);
        run.setEndSeq(endSeq);
        run.setGmtCreate(CREATED);
        run.setGmtModified(CREATED.plusMinutes(1));
        return runRepository.insert(run).getId();
    }

    private void seedEvents(long conversationId, long firstRunId, long secondRunId) {
        for (int seq = 1; seq <= EVENT_COUNT; seq++) {
            int turn = seq <= EVENTS_PER_RUN ? 1 : 2;
            long runId = turn == 1 ? firstRunId : secondRunId;
            AiEventCodec.Payload payload = AiEventCodec
                    .write(objectMapper, runId, sampleEvent(seq, turn))
                    .orElseThrow(() -> new IllegalStateException("a fixture event must serialise"));
            RmqAiEvent row = new RmqAiEvent();
            row.setConversationId(conversationId);
            row.setRunId(runId);
            row.setTurn(turn);
            row.setSeq(seq);
            row.setType(payload.type());
            row.setPayload(payload.json());
            row.setGmtCreate(CREATED.plusSeconds(seq));
            row.setGmtModified(CREATED.plusSeconds(seq));
            eventRepository.insert(row);
        }
    }

    /** One of every stored shape per run, then plain text, so the payload column is not a single type. */
    private static TimelineEvent sampleEvent(int seq, int turn) {
        int position = (seq - 1) % EVENTS_PER_RUN;
        if (position == 0) {
            return new TimelineEvent.User("why is turn " + turn + " slow",
                    turn == 1 ? "rewritten: why is turn 1 slow" : null);
        }
        if (position == 1) {
            return new TimelineEvent.Thinking("reading the offsets of turn " + turn, ThinkingSource.MODEL);
        }
        if (position == 2) {
            return new TimelineEvent.ToolUse("tc-" + turn, "rmq.group.detail",
                    Map.of("groupName", "GID_" + turn));
        }
        if (position == 3) {
            return new TimelineEvent.ToolResult("tc-" + turn, "rmq.group.detail", "lag 4711", 8, false,
                    true, 12L, null);
        }
        if (position == EVENTS_PER_RUN - 1) {
            return new TimelineEvent.RunStatus(RunStatus.COMPLETED, null);
        }
        return new TimelineEvent.Text("answer " + seq);
    }

    private void markRunning(long runId) {
        RmqAiRun run = new RmqAiRun();
        run.setId(runId);
        run.setStatus(RunStatus.RUNNING.name());
        run.setGmtModified(LocalDateTime.now());
        runRepository.update(run);
    }

    private void markCompleted(long runId) {
        RmqAiRun run = new RmqAiRun();
        run.setId(runId);
        run.setStatus(RunStatus.COMPLETED.name());
        run.setGmtModified(LocalDateTime.now());
        runRepository.update(run);
    }

    /** The same conversation with a gmt_create of now, i.e. inside any retention window. */
    private RmqAiConversation recentCopyOf(long conversationId) {
        RmqAiConversation recent = new RmqAiConversation();
        recent.setId(conversationId);
        recent.setGmtCreate(LocalDateTime.now());
        return recent;
    }

    /**
     * Moves a conversation's {@code gmt_modified}, i.e. "somebody opened it again". Written through the
     * mapper and not through the spied repository, so the retention sweep's call order stays readable.
     */
    private void touch(long conversationId, LocalDateTime gmtModified) {
        RmqAiConversation touched = new RmqAiConversation();
        touched.setId(conversationId);
        touched.setGmtModified(gmtModified);
        conversationMapper.updateById(touched);
    }

    private long countEvents(long conversationId) {
        return eventMapper.selectCount(new QueryWrapper<RmqAiEvent>().eq("conversation_id", conversationId));
    }

    private long countRuns(long conversationId) {
        return runMapper.selectCount(new QueryWrapper<RmqAiRun>().eq("conversation_id", conversationId));
    }

    private static List<Integer> expectedSeqs(int from, int toInclusive) {
        return IntStream.rangeClosed(from, toInclusive).boxed().toList();
    }

    /** The ids one {@link #seed()} produced. */
    private record Seeded(long conversationId, long firstRunId, long secondRunId) {
    }
}
