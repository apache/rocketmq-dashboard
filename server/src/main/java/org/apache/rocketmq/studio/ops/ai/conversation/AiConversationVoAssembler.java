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
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEventProjector;
import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;
import org.apache.rocketmq.studio.ops.ai.conversation.event.StopReason;
import org.apache.rocketmq.studio.ops.ai.conversation.event.TimelineEvent;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiActiveRunRef;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiConversationDetailVO;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiConversationListItemVO;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiConversationVO;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiRunStatsVO;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiRunVO;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiTimelineItemVO;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiTimelineVO;
import org.apache.rocketmq.studio.persistence.entity.RmqAiConversation;
import org.apache.rocketmq.studio.persistence.entity.RmqAiEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiRun;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Entity to VO mapping for the conversation REST surface. The VO field names are a frozen
 * cross-language contract ({@code web/src/api/aiEvents.ts} / {@code aiConversations.ts}); this class
 * is the single place where the persistence shape is translated into it, so a rename can only happen
 * here and the assembler test fails when it does.
 *
 * <p>Two renames live here on purpose: {@code gmtCreate}/{@code gmtModified} become
 * {@code createdAt}/{@code updatedAt} on the wire, and the {@code payload} column becomes the
 * deserialised {@link TimelineEvent} under {@code event}. Decoding goes through the package-private
 * {@link AiEventCodec} — which is why this class sits in {@code ops.ai.conversation} rather than in
 * the {@code vo} subpackage — so one unreadable row degrades to a placeholder notice instead of
 * costing the user the whole timeline (the {@code QueryHistoryService} snapshot discipline).
 *
 * <p>Enum parsing is split by contract strength: {@code status} is required by the TS types and is
 * only ever written from {@link RunStatus#name()}, so an unparseable value is a data bug and fails
 * loudly; the optional columns ({@code stop_reason}, the list item's {@code lastRunStatus}) degrade
 * to null, which the TS types explicitly allow.
 */
@Slf4j
public final class AiConversationVoAssembler {

    /** Placeholder for a row whose payload cannot be decoded; level {@code warn} is inside the TS {@code NoticeLevel} union. */
    static final String UNREADABLE_EVENT_MESSAGE =
            "stored event could not be decoded; its original content is unavailable";

    private AiConversationVoAssembler() {
    }

    /** The conversation on its own — the shape POST/PATCH answer with. */
    public static AiConversationVO toConversationVo(RmqAiConversation entity) {
        AiConversationVO.AiConversationVOBuilder<?, ?> builder = AiConversationVO.builder();
        applyConversation(builder, entity);
        return builder.build();
    }

    /**
     * The conversation plus the run still generating. {@code activeRun} may be null (idle
     * conversation); when present it must be QUEUED or RUNNING — the caller filters.
     */
    public static AiConversationDetailVO toConversationDetailVo(RmqAiConversation entity, RmqAiRun activeRun) {
        AiConversationDetailVO.AiConversationDetailVOBuilder<?, ?> builder = AiConversationDetailVO.builder();
        applyConversation(builder, entity);
        return builder.activeRun(toActiveRunRef(activeRun)).build();
    }

    /**
     * One list row. {@code lastRun} is the conversation's newest run and may be null for a
     * conversation that has never been used.
     */
    public static AiConversationListItemVO toListItemVo(RmqAiConversation entity, RmqAiRun lastRun) {
        return AiConversationListItemVO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .engine(entity.getEngine())
                .model(entity.getModel())
                .mode(entity.getMode())
                .instanceId(entity.getInstanceId())
                .lastRunId(lastRun == null ? null : lastRun.getId())
                .lastRunStatus(lastRun == null ? null : parseRunStatusOrNull(lastRun.getStatus()))
                .updatedAt(entity.getGmtModified())
                .createdAt(entity.getGmtCreate())
                .build();
    }

    /** A run row, e.g. the answer of POST {@code /api/ai/runs/{runId}/stop}. */
    public static AiRunVO toRunVo(RmqAiRun entity) {
        return AiRunVO.builder()
                .id(entity.getId())
                .conversationId(entity.getConversationId())
                .turn(entity.getTurn())
                .status(parseRunStatus(entity.getStatus()))
                .engine(entity.getEngine())
                .model(entity.getModel())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .durationMs(entity.getDurationMs())
                .inputTokens(entity.getInputTokens())
                .outputTokens(entity.getOutputTokens())
                .stopReason(parseStopReason(entity.getStopReason()))
                .errorCode(entity.getErrorCode())
                .errorMessage(entity.getErrorMessage())
                .build();
    }

    /** The {@code {id, status}} reference shared by the detail VO and the timeline envelope. */
    public static AiActiveRunRef toActiveRunRef(RmqAiRun run) {
        if (run == null) {
            return null;
        }
        return AiActiveRunRef.builder()
                .id(run.getId())
                .status(parseRunStatus(run.getStatus()))
                .build();
    }

    /**
     * One raw timeline row, payload decoded here. Never throws on a bad payload: the event degrades to a
     * warn-level {@link TimelineEvent.Notice} placeholder so the rest of the conversation still renders.
     *
     * <p>That is deliberately a <em>different</em> policy from the REST timeline, which goes through
     * {@link AiConversationService#timeline} and SKIPS a row it cannot decode (the cursor still advances
     * past it, so paging cannot get stuck). This variant is the "I am holding a row and must not lose it"
     * primitive; both build the VO through {@link #toTimelineItemVo(AiConversationService.TimelineItem)}
     * so the field mapping itself cannot drift between the two.
     */
    public static AiTimelineItemVO toTimelineItemVo(ObjectMapper objectMapper, RmqAiEvent row) {
        TimelineEvent event = AiEventCodec.read(objectMapper, row)
                .orElseGet(() -> new TimelineEvent.Notice(AgentEventProjector.LEVEL_WARN, UNREADABLE_EVENT_MESSAGE));
        return toTimelineItemVo(new AiConversationService.TimelineItem(row.getId(), row.getTurn(), row.getSeq(),
                row.getGmtCreate(), row.getRunId(), event));
    }

    /**
     * One timeline row the service has already decoded. This is the mapping the REST surface uses: the
     * event is never null here, so no {@link ObjectMapper} is involved.
     */
    public static AiTimelineItemVO toTimelineItemVo(AiConversationService.TimelineItem item) {
        return AiTimelineItemVO.builder()
                .id(item.id())
                .turn(item.turn())
                .seq(item.seq())
                .createdAt(item.createdAt())
                .runId(item.runId())
                .event(item.event())
                .build();
    }

    /**
     * The whole timeline page as the service returns it — the answer of
     * {@code GET /api/ai/conversations/{id}/events}.
     */
    public static AiTimelineVO toTimelineVo(AiConversationService.TimelinePage page) {
        if (page == null) {
            return toTimelineVo(List.of(), null, null);
        }
        List<AiTimelineItemVO> items = new ArrayList<>(page.items().size());
        for (AiConversationService.TimelineItem item : page.items()) {
            items.add(toTimelineItemVo(item));
        }
        List<AiRunStatsVO> runs = new ArrayList<>(page.runs().size());
        for (RmqAiRun run : page.runs()) {
            runs.add(AiRunStatsVO.builder()
                    .id(run.getId())
                    .tokensPerSecond(run.getTokensPerSecond())
                    .build());
        }
        AiTimelineVO vo = toTimelineVo(items, page.nextAfter(), page.activeRun());
        vo.setRuns(runs);
        return vo;
    }

    /** The timeline page envelope; {@code items} and {@code runs} are never null on the wire. */
    public static AiTimelineVO toTimelineVo(List<AiTimelineItemVO> items, Integer nextAfter, RmqAiRun activeRun) {
        return AiTimelineVO.builder()
                .items(items == null ? List.of() : items)
                .nextAfter(nextAfter)
                .activeRun(toActiveRunRef(activeRun))
                .runs(List.of())
                .build();
    }

    /**
     * The one mapping of a conversation row into the contract fields, shared by the plain and the
     * detail VO so the two cannot drift. The builder parameter is the {@code @SuperBuilder} type,
     * which is what lets the detail VO's builder pass through here.
     */
    private static void applyConversation(AiConversationVO.AiConversationVOBuilder<?, ?> builder,
                                          RmqAiConversation entity) {
        builder.id(entity.getId())
                .title(entity.getTitle())
                .owner(entity.getOwner())
                .engine(entity.getEngine())
                .model(entity.getModel())
                .mode(entity.getMode())
                .instanceId(entity.getInstanceId())
                .runtimeSessionId(entity.getRuntimeSessionId())
                // lastSeq and archived are required (non-null) in the TS contract; the columns have
                // DB defaults but a hand-built entity may not, so both get an explicit fallback.
                .lastSeq(entity.getLastSeq() == null ? 0 : entity.getLastSeq())
                .archived(Boolean.TRUE.equals(entity.getArchived()))
                .createdAt(entity.getGmtCreate())
                .updatedAt(entity.getGmtModified());
    }

    /** {@code status} is required by the TS contract; fail loudly on data that cannot honour it. */
    private static RunStatus parseRunStatus(String value) {
        return RunStatus.valueOf(value);
    }

    private static RunStatus parseRunStatusOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return RunStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            log.warn("unknown run status '{}' in stored data; exposing null", value);
            return null;
        }
    }

    private static StopReason parseStopReason(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return StopReason.valueOf(value);
        } catch (IllegalArgumentException exception) {
            log.warn("unknown stop reason '{}' in stored data; exposing null", value);
            return null;
        }
    }
}
