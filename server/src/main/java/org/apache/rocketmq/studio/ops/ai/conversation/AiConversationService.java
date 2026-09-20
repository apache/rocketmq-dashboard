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

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.ai.LlmConfigService;
import org.apache.rocketmq.studio.ops.ai.LlmConfigVO;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.RmqctlWorkspace;
import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;
import org.apache.rocketmq.studio.ops.ai.conversation.event.StopReason;
import org.apache.rocketmq.studio.ops.ai.conversation.event.TimelineEvent;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiConversationListItemVO;
import org.apache.rocketmq.studio.persistence.entity.RmqAiConversation;
import org.apache.rocketmq.studio.persistence.entity.RmqAiEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Conversation CRUD, the persisted timeline read, and the two sweeps that keep {@code rmq_ai_run}
 * honest about runs nobody is executing any more.
 *
 * <h2>Owner is a filter, not an ACL</h2>
 * Every lookup is scoped to {@code AuthenticatedUserContext.currentUsernameOrSystem()} and a
 * conversation belonging to somebody else is reported as <strong>404</strong>, never 403: an
 * authorisation error tells an attacker that the id exists. This is the same guard
 * {@code QueryHistoryService.getMessageQueryResults} uses, and this deployment has no permission
 * system to delegate to.
 *
 * <h2>No lease, so two sweeps instead</h2>
 * A run's owner is a thread in this JVM and there is exactly one JVM, so a row that still says QUEUED
 * or RUNNING while nothing here owns it is definitionally orphaned. That is decided in two places
 * rather than by a heartbeat:
 * <ul>
 *   <li>{@link #run(ApplicationArguments)} at startup — anything non-terminal survived a restart, and
 *       nothing survived, so all of it becomes {@code FAILED/SERVER_RESTART};</li>
 *   <li>the orphan sweep inside {@link #purgeExpired()} — a run non-terminal past
 *       {@code studio.ai.conversation.orphan-run-timeout} with no handle in
 *       {@link AgentRunRegistry} becomes {@code FAILED/ORPHANED}. One scheduled method for both sweeps
 *       and retention, not three: they share a clock, a failure policy and an operator's attention.</li>
 * </ul>
 * Neither writes a {@code run_status} timeline event, because neither has a sink and neither knows what
 * the run had already produced. <strong>That is why a reload must consult {@code rmq_ai_run.status} and
 * not only the events</strong>: for a reaped run the row is the only record of how it ended.
 *
 * <h2>Retention cascades by hand</h2>
 * This project declares no foreign keys, so deleting a conversation has to delete its runs and events
 * itself, and the order is events, runs, conversations: a crash halfway leaves a parent with no
 * children, which the next pass finishes, instead of children nobody can find any more.
 */
@Slf4j
@Service
public class AiConversationService implements ApplicationRunner {

    /**
     * Placeholder title of a conversation that has not carried a message yet, replaced by
     * {@link #deriveTitle(String)} on the first turn. Spelled with unicode escapes because
     * {@code style/rmq_checkstyle.xml} rejects non-ASCII characters in Java sources; it reads
     * "new conversation".
     */
    static final String DEFAULT_TITLE = "\u65B0\u4F1A\u8BDD";

    /** Characters of the first message a derived title keeps. */
    static final int TITLE_MAX_CHARS = 40;

    /** {@code rmq_ai_conversation.title} is VARCHAR(512); an explicit rename is capped at that. */
    static final int TITLE_COLUMN_MAX_CHARS = 512;

    static final String DEFAULT_MODE = "chat";
    static final Set<String> MODES = Set.of("chat", "diagnose", "manage", "query");

    /** Cursor page defaults of {@code GET /conversations/{id}/events}. */
    static final int DEFAULT_TIMELINE_LIMIT = 200;
    static final int MAX_TIMELINE_LIMIT = 500;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_CLEANUP_BATCH_SIZE = 500;
    private static final int MAX_CLEANUP_BATCH_SIZE = 5_000;
    private static final int DEFAULT_CLEANUP_MAX_BATCHES = 20;
    private static final int MAX_CLEANUP_MAX_BATCHES = 100;

    private final AiConversationRepository conversationRepository;
    private final AiRunRepository runRepository;
    private final AiEventRepository eventRepository;
    private final AgentRunRegistry registry;
    private final RmqctlWorkspace workspace;
    private final LlmConfigService llmConfigService;
    private final AiConversationProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AiConversationService(AiConversationRepository conversationRepository,
                                 AiRunRepository runRepository,
                                 AiEventRepository eventRepository,
                                 AgentRunRegistry registry,
                                 RmqctlWorkspace workspace,
                                 LlmConfigService llmConfigService,
                                 AiConversationProperties properties,
                                 ObjectMapper objectMapper) {
        this(conversationRepository, runRepository, eventRepository, registry, workspace, llmConfigService,
                properties, objectMapper, Clock.systemUTC());
    }

    /** Visible for tests: an injectable clock makes the retention cutoff and the sweeps assertable. */
    AiConversationService(AiConversationRepository conversationRepository,
                          AiRunRepository runRepository,
                          AiEventRepository eventRepository,
                          AgentRunRegistry registry,
                          RmqctlWorkspace workspace,
                          LlmConfigService llmConfigService,
                          AiConversationProperties properties,
                          ObjectMapper objectMapper,
                          Clock clock) {
        this.conversationRepository = conversationRepository;
        this.runRepository = runRepository;
        this.eventRepository = eventRepository;
        this.registry = registry;
        this.workspace = workspace;
        this.llmConfigService = llmConfigService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Creates an empty conversation bound to the calling operator.
     *
     * <p>{@code engine} and {@code model} are snapshotted from the stored LLM configuration so the
     * conversation has an answer before its first message; a run re-resolves them at admission, so
     * configuring a provider after creating the conversation still works.
     */
    public RmqAiConversation create(String owner, String instanceId, String mode) {
        LlmConfigVO config = llmConfigService.getConfig();
        LocalDateTime now = LocalDateTime.now(clock);
        RmqAiConversation conversation = new RmqAiConversation();
        conversation.setTitle(DEFAULT_TITLE);
        conversation.setOwner(StringUtils.hasText(owner) ? owner.trim() : "system");
        conversation.setEngine(config == null ? "" : config.normalizeEngine());
        conversation.setModel(config == null || config.getModel() == null ? "" : config.getModel().trim());
        conversation.setMode(normalizeMode(mode));
        conversation.setInstanceId(StringUtils.hasText(instanceId) ? instanceId.trim() : null);
        conversation.setLastSeq(0);
        conversation.setArchived(false);
        conversation.setGmtCreate(now);
        conversation.setGmtModified(now);
        RmqAiConversation inserted = conversationRepository.insert(conversation);
        log.info("created AI conversation {} for {} (instance={}, mode={})", inserted.getId(),
                inserted.getOwner(), inserted.getInstanceId(), inserted.getMode());
        return inserted;
    }

    /**
     * Paged list of one operator's conversations, most recently used first. The ordering is
     * {@code gmt_modified desc, id desc} — deliberately not this project's usual {@code gmt_create} —
     * because a conversation resumed today has to float to the top.
     */
    public PageResult<RmqAiConversation> list(String owner, String search, Boolean archived,
                                              int page, int size) {
        int boundedPage = Math.max(1, page);
        int boundedSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return conversationRepository.findPage(owner, search, archived, boundedPage, boundedSize);
    }

    /**
     * The same page as {@link #list}, mapped into the REST contract: every row carries its newest run
     * so the list can show {@code lastRunId} / {@code lastRunStatus}.
     *
     * <p>The runs are resolved with one batch query rather than one per row — a page of a hundred
     * conversations must not cost a hundred round trips — and a conversation that has never run simply
     * has no entry, which the assembler renders as the two nulls the TS type allows.
     */
    public PageResult<AiConversationListItemVO> listItems(String owner, String search, Boolean archived,
                                                          int page, int size) {
        PageResult<RmqAiConversation> conversations = list(owner, search, archived, page, size);
        List<RmqAiConversation> rows = conversations.getItems();
        if (rows.isEmpty()) {
            return PageResult.of(List.of(), conversations.getTotal(),
                    conversations.getPage(), conversations.getSize());
        }
        Map<Long, RmqAiRun> latestRuns = runRepository.findLatestByConversationIds(
                rows.stream().map(RmqAiConversation::getId).toList());
        List<AiConversationListItemVO> items = rows.stream()
                .map(row -> AiConversationVoAssembler.toListItemVo(row, latestRuns.get(row.getId())))
                .toList();
        return PageResult.of(items, conversations.getTotal(), conversations.getPage(), conversations.getSize());
    }

    /**
     * Owner-scoped lookup.
     *
     * @throws BusinessException 404 when the conversation does not exist <em>or</em> belongs to
     *     somebody else; the two are indistinguishable on purpose
     */
    public RmqAiConversation requireOwned(Long conversationId, String owner) {
        return conversationRepository.findByIdAndOwner(conversationId, owner)
                .orElseThrow(() -> {
                    log.debug("AI conversation {} not found for owner {}", conversationId, owner);
                    return new BusinessException(404, "AI conversation not found");
                });
    }

    /** The same lookup against the authenticated operator. */
    public RmqAiConversation requireOwned(Long conversationId) {
        return requireOwned(conversationId, currentOwner());
    }

    public Optional<RmqAiRun> activeRun(Long conversationId) {
        return runRepository.findActiveByConversationId(conversationId);
    }

    /** Renames and/or archives. Fields left null are untouched. */
    public RmqAiConversation update(Long conversationId, String owner, String title, Boolean archived) {
        RmqAiConversation conversation = requireOwned(conversationId, owner);
        RmqAiConversation update = new RmqAiConversation();
        update.setId(conversation.getId());
        boolean changed = false;
        if (StringUtils.hasText(title)) {
            String capped = capTitle(title.trim());
            // Only a real change is written: an update that touches nothing must not bump
            // gmt_modified, or renaming a conversation to what it already is reorders the list.
            if (!capped.equals(conversation.getTitle())) {
                update.setTitle(capped);
                changed = true;
            }
        }
        if (archived != null && !archived.equals(Boolean.TRUE.equals(conversation.getArchived()))) {
            update.setArchived(archived);
            changed = true;
        }
        if (!changed) {
            return conversation;
        }
        update.setGmtModified(LocalDateTime.now(clock));
        conversationRepository.update(update);
        return requireOwned(conversationId, owner);
    }

    /**
     * Deletes a conversation, its runs, its events and its agent workspace.
     *
     * <p>An active run is stopped first so its subprocess is not left writing into a workspace that is
     * being removed. A residual race remains — the worker writes its terminal state asynchronously and
     * may insert one {@code run_status} row after the events were deleted — and the honest consequence
     * is a single orphan event row, not a resurrected conversation. It is not worth a lock to prevent.
     */
    public void delete(Long conversationId, String owner) {
        RmqAiConversation conversation = requireOwned(conversationId, owner);
        runRepository.findActiveByConversationId(conversationId).ifPresent(active -> {
            log.info("stopping agent run {} before deleting conversation {}", active.getId(), conversationId);
            registry.stop(active.getId(), AbortReason.USER_STOP);
        });
        int events = eventRepository.deleteByConversationId(conversationId);
        int runs = runRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
        workspace.delete(conversationId);
        log.info("deleted AI conversation {} ({} run(s), {} event(s))", conversationId, runs, events);
    }

    /**
     * The persisted timeline of one conversation, cursor-paged on {@code seq}.
     *
     * <p>The repository query deliberately has no SQL {@code ORDER BY}: {@code payload} is MEDIUMTEXT
     * and a sort MySQL cannot serve from {@code uk_ai_event_conversation_seq} materialises the whole
     * value into {@code sort_buffer_size}. It fetches {@code limit + 1} rows so {@code nextAfter} can be
     * derived without a second query.
     */
    public TimelinePage timeline(Long conversationId, String owner, int afterSeq, int limit) {
        RmqAiConversation conversation = requireOwned(conversationId, owner);
        int bounded = limit <= 0 ? DEFAULT_TIMELINE_LIMIT : Math.min(limit, MAX_TIMELINE_LIMIT);
        List<RmqAiEvent> rows = eventRepository.findByConversationIdAfterSeq(conversation.getId(),
                Math.max(0, afterSeq), bounded + 1);
        boolean truncated = rows.size() > bounded;
        List<RmqAiEvent> page = truncated ? rows.subList(0, bounded) : rows;
        List<TimelineItem> items = new ArrayList<>(page.size());
        for (RmqAiEvent row : page) {
            TimelineEvent event = decode(row);
            if (event != null) {
                items.add(new TimelineItem(row.getId(), row.getTurn(), row.getSeq(), row.getGmtCreate(),
                        row.getRunId(), event));
            }
        }
        Integer nextAfter = page.isEmpty() ? null : page.get(page.size() - 1).getSeq();
        return new TimelinePage(items, nextAfter, activeRun(conversation.getId()).orElse(null),
                runsReferencedBy(conversation.getId(), items));
    }

    /**
     * The run rows the page references, in first-appearance order. A conversation accumulates at
     * most one run per turn, so filtering the conversation's full run list is cheaper than a
     * per-page {@code WHERE id IN (...)} and keeps the repository port minimal.
     */
    private List<RmqAiRun> runsReferencedBy(Long conversationId, List<TimelineItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashSet<Long> referenced = new java.util.LinkedHashSet<>();
        for (TimelineItem item : items) {
            referenced.add(item.runId());
        }
        List<RmqAiRun> runs = new ArrayList<>();
        for (RmqAiRun run : runRepository.findByConversationId(conversationId)) {
            if (referenced.contains(run.getId())) {
                runs.add(run);
            }
        }
        return runs;
    }

    /**
     * The title of a conversation, derived from its first user message rather than generated by a model:
     * newlines folded to spaces, then the first {@value #TITLE_MAX_CHARS} characters. An extra model
     * call per conversation would cost latency and a failure mode for something a rule does well enough.
     */
    public static String deriveTitle(String message) {
        if (!StringUtils.hasText(message)) {
            return DEFAULT_TITLE;
        }
        String folded = message.strip().replaceAll("\\s+", " ");
        if (folded.length() <= TITLE_MAX_CHARS) {
            return folded;
        }
        return folded.substring(0, TITLE_MAX_CHARS);
    }

    /** Caps an explicitly supplied title at the column width. */
    static String capTitle(String title) {
        if (title == null) {
            return null;
        }
        return title.length() <= TITLE_COLUMN_MAX_CHARS ? title : title.substring(0, TITLE_COLUMN_MAX_CHARS);
    }

    static String normalizeMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return DEFAULT_MODE;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        return MODES.contains(normalized) ? normalized : DEFAULT_MODE;
    }

    /** The operator whose conversations every read and write in this service is scoped to. */
    static String currentOwner() {
        return AuthenticatedUserContext.currentUsernameOrSystem();
    }

    /**
     * Startup reaper: every run left non-terminal by the previous process is orphaned, because the
     * thread that owned it did not survive. Marked {@code FAILED/SERVER_RESTART} rather than
     * {@code STOPPED}, since nothing chose to stop it.
     *
     * <p>A failure here must not abort startup — a database that has not been migrated yet is exactly
     * the case the schema runner is about to fix — so it is caught and logged.
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            List<RmqAiRun> orphans = runRepository.findByStatusIn(RunStatus.ACTIVE_STATUSES);
            if (orphans.isEmpty()) {
                return;
            }
            LocalDateTime now = LocalDateTime.now(clock);
            orphans.forEach(run -> markTerminal(run, RunStatus.FAILED, StopReason.SERVER_RESTART, now));
            log.warn("startup reaper marked {} agent run(s) FAILED/SERVER_RESTART", orphans.size());
        } catch (RuntimeException exception) {
            log.warn("could not reap agent runs left over from the previous process: {}",
                    exception.toString());
        }
    }

    /**
     * Retention plus the orphan sweep, in one scheduled pass.
     *
     * <p>Failures are caught and logged, never propagated: a scheduler thread that dies on a database
     * blip stops every later pass silently, which is how a retention job turns into a full disk.
     */
    @Scheduled(fixedDelayString = "${studio.ai.conversation.cleanup-interval:PT24H}")
    public void purgeExpired() {
        reapOrphanedRuns();
        int retentionDays = properties.getRetentionDays();
        if (retentionDays <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retentionDays);
        try {
            int deleted = deleteExpiredConversations(cutoff);
            if (deleted > 0) {
                log.info("purged {} expired AI conversation(s) and their runs and events", deleted);
            }
        } catch (RuntimeException exception) {
            log.warn("failed to purge expired AI conversations: {}", exception.getMessage());
        }
    }

    /**
     * Reaps runs the database still calls active that nothing in this JVM owns. A live handle means the
     * run is merely slow — the CLI budget is five minutes and the timeout is ten — so only handle-less
     * rows are touched.
     */
    private void reapOrphanedRuns() {
        Duration timeout = properties.getOrphanRunTimeout();
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            return;
        }
        try {
            LocalDateTime cutoff = LocalDateTime.now(clock).minus(timeout);
            List<RmqAiRun> stale = runRepository.findStaleActive(cutoff, RunStatus.ACTIVE_STATUSES);
            if (stale.isEmpty()) {
                return;
            }
            LocalDateTime now = LocalDateTime.now(clock);
            int reaped = 0;
            for (RmqAiRun run : stale) {
                if (registry.isLive(run.getId())) {
                    log.debug("agent run {} is non-terminal but still owned here; leaving it alone",
                            run.getId());
                    continue;
                }
                markTerminal(run, RunStatus.FAILED, StopReason.ORPHANED, now);
                reaped++;
            }
            if (reaped > 0) {
                log.warn("reaped {} orphaned agent run(s) with no owner in this process", reaped);
            }
        } catch (RuntimeException exception) {
            log.warn("failed to reap orphaned AI runs: {}", exception.getMessage());
        }
    }

    /**
     * Writes a terminal state for a run whose worker is gone. No {@code run_status} event is written:
     * there is no sink, no seq to allocate it against, and guessing would corrupt the timeline. Readers
     * must fall back to the row, which is why {@link TimelinePage#activeRun()} exists.
     */
    private void markTerminal(RmqAiRun run, RunStatus status, StopReason reason, LocalDateTime now) {
        run.setStatus(status.name());
        run.setStopReason(reason.name());
        run.setFinishedAt(now);
        run.setDurationMs(run.getStartedAt() == null
                ? null
                : Math.max(0L, Duration.between(run.getStartedAt(), now).toMillis()));
        run.setGmtModified(now);
        runRepository.update(run);
    }

    private int deleteExpiredConversations(LocalDateTime cutoff) {
        int batchSize = boundedPositive(properties.getCleanupBatchSize(),
                DEFAULT_CLEANUP_BATCH_SIZE, MAX_CLEANUP_BATCH_SIZE);
        int maxBatches = boundedPositive(properties.getCleanupMaxBatches(),
                DEFAULT_CLEANUP_MAX_BATCHES, MAX_CLEANUP_MAX_BATCHES);
        int totalDeleted = 0;
        for (int batch = 0; batch < maxBatches; batch++) {
            List<Long> ids = conversationRepository.findIdsCreatedBefore(cutoff, batchSize);
            if (ids.isEmpty()) {
                break;
            }
            int deleted = deleteCascade(ids);
            totalDeleted += deleted;
            if (deleted < batchSize) {
                break;
            }
        }
        return totalDeleted;
    }

    /** Events, then runs, then conversations: no FK constraints here, so the cascade is this method. */
    private int deleteCascade(List<Long> conversationIds) {
        eventRepository.deleteByConversationIds(conversationIds);
        runRepository.deleteByConversationIds(conversationIds);
        return conversationRepository.deleteByIds(conversationIds);
    }

    /**
     * Decodes one row through {@link AiEventCodec}. An unreadable payload is skipped rather than
     * fatal: one truncated row must not cost the user the whole conversation.
     */
    private TimelineEvent decode(RmqAiEvent row) {
        return AiEventCodec.read(objectMapper, row).orElse(null);
    }

    private static int boundedPositive(int value, int defaultValue, int maxValue) {
        if (value <= 0) {
            return defaultValue;
        }
        return Math.min(value, maxValue);
    }

    /**
     * One persisted timeline row, decoded, with the cursor and bubble metadata the UI needs.
     *
     * <p>The component order mirrors {@code AiTimelineItemVO} one for one so the mapping in
     * {@link AiConversationVoAssembler} stays positional and obvious.
     *
     * <p>{@code id} is the {@code rmq_ai_event} primary key and {@code runId} the run that produced the
     * row. Both are part of the frozen contract ({@code TimelineItem} in {@code web/src/api/aiEvents.ts}),
     * where {@code runId} is required precisely because {@code rmq_ai_event.run_id} is NOT NULL: an
     * envelope must not be weaker than the storage, and every event belongs to exactly one run.
     */
    public record TimelineItem(Long id, Integer turn, Integer seq, LocalDateTime createdAt,
                               Long runId, TimelineEvent event) {
    }

    /**
     * A page of the timeline plus the run still generating, so a reload can re-attach instead of
     * showing a dead transcript.
     *
     * @param nextAfter the cursor for the next page, or null when this page reached the end
     */
    public record TimelinePage(List<TimelineItem> items, Integer nextAfter, RmqAiRun activeRun,
                               List<RmqAiRun> runs) {
        /** A page with no run stats — callers that do not need them keep the short form. */
        public TimelinePage(List<TimelineItem> items, Integer nextAfter, RmqAiRun activeRun) {
            this(items, nextAfter, activeRun, List.of());
        }
    }
}
