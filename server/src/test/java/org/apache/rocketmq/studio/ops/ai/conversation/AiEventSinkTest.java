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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEventProjector;
import org.apache.rocketmq.studio.ops.ai.conversation.event.TimelineEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AiEventSinkTest {

    private static final long CONVERSATION = 7L;
    private static final long RUN = 11L;
    private static final int TURN = 3;
    private static final int SEED_SEQ = 100;

    private AiEventRepository eventRepository;
    private AiConversationRepository conversationRepository;
    private final List<RmqAiEvent> inserted = new ArrayList<>();
    private final List<long[]> cacheUpdates = new ArrayList<>();
    private final List<Object[]> published = new ArrayList<>();

    @BeforeEach
    void setUp() {
        inserted.clear();
        cacheUpdates.clear();
        published.clear();
        eventRepository = mock(AiEventRepository.class);
        conversationRepository = mock(AiConversationRepository.class);
        doAnswer(invocation -> {
            inserted.add(invocation.getArgument(0));
            return null;
        }).when(eventRepository).insert(any(RmqAiEvent.class));
        doAnswer(invocation -> {
            org.apache.rocketmq.studio.persistence.entity.RmqAiConversation update = invocation.getArgument(0);
            cacheUpdates.add(new long[] {update.getId(), update.getLastSeq()});
            return 1;
        }).when(conversationRepository).update(
                any(org.apache.rocketmq.studio.persistence.entity.RmqAiConversation.class));
    }

    private AiEventSink newSink() {
        return new AiEventSink(CONVERSATION, RUN, TURN, SEED_SEQ, eventRepository, conversationRepository,
                new AgentEventProjector(RUN), new ObjectMapper(),
                (seq, event) -> published.add(new Object[] {seq, event}), null);
    }

    @Test
    void writeUserAllocatesTheNextSeqAndCarriesTheRowIdentity() {
        AiEventSink sink = newSink();

        int seq = sink.writeUser("hello", null);

        assertThat(seq).isEqualTo(SEED_SEQ + 1);
        assertThat(sink.highWaterSeq()).isEqualTo(SEED_SEQ + 1);
        assertThat(inserted).hasSize(1);
        RmqAiEvent row = inserted.get(0);
        assertThat(row.getConversationId()).isEqualTo(CONVERSATION);
        assertThat(row.getRunId()).isEqualTo(RUN);
        assertThat(row.getTurn()).isEqualTo(TURN);
        assertThat(row.getSeq()).isEqualTo(SEED_SEQ + 1);
        assertThat(row.getType()).isEqualTo("user");
    }

    @Test
    void contiguousSeqsAreAllocatedAcrossWrites() {
        AiEventSink sink = newSink();

        sink.writeUser("first", null);
        sink.writeTimeline(new TimelineEvent.Notice("info", "mid"));
        int last = sink.writeTimeline(new TimelineEvent.Notice("warn", "last"));

        assertThat(last).isEqualTo(SEED_SEQ + 3);
        assertThat(inserted).extracting(RmqAiEvent::getSeq)
                .containsExactly(SEED_SEQ + 1, SEED_SEQ + 2, SEED_SEQ + 3);
    }

    @Test
    void lastSeqCacheIsRefreshedOnlyWhenTheHighWaterMarkMoves() {
        AiEventSink sink = newSink();

        sink.writeUser("hello", null);
        sink.writeUser("again", null);

        // One cache refresh per move: seed -> 101 -> 102.
        assertThat(cacheUpdates).hasSize(2);
        assertThat(cacheUpdates.get(0)).containsExactly(CONVERSATION, SEED_SEQ + 1);
        assertThat(cacheUpdates.get(1)).containsExactly(CONVERSATION, SEED_SEQ + 2);
    }

    @Test
    void flushPendingWithoutBufferedContentKeepsTheHighWaterMark() {
        AiEventSink sink = newSink();

        int seq = sink.flushPending();

        assertThat(seq).isEqualTo(SEED_SEQ);
        assertThat(sink.highWaterSeq()).isEqualTo(SEED_SEQ);
        assertThat(inserted).isEmpty();
    }

    @Test
    void textDeltasAreBufferedNotPersistedAndLiveSeqIsZero() {
        AiEventSink sink = newSink();

        sink.emit(new org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEvent.TextDelta("tok"));

        assertThat(sink.hasPending()).isTrue();
        assertThat(inserted).isEmpty();
        assertThat(published).hasSize(1);
        assertThat(published.get(0)[0]).isEqualTo(0L);
    }

    @Test
    void aDurabilityBoundaryFlushesTheBufferAheadOfItself() {
        AiEventSink sink = newSink();
        sink.emit(new org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEvent.TextDelta("tok"));

        int seq = sink.writeTimeline(new TimelineEvent.Notice("warn", "boundary"));

        assertThat(sink.hasPending()).isFalse();
        assertThat(seq).isEqualTo(SEED_SEQ + 2);
        assertThat(inserted).extracting(RmqAiEvent::getSeq)
                .containsExactly(SEED_SEQ + 1, SEED_SEQ + 2);
        assertThat(inserted).extracting(RmqAiEvent::getType).containsExactly("text", "notice");
    }

    @Test
    void closeFlushesTheBufferAndIsIdempotent() {
        AiEventSink sink = newSink();
        sink.emit(new org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEvent.TextDelta("tok"));

        sink.close();
        int rowsAfterFirstClose = inserted.size();
        sink.close();

        assertThat(rowsAfterFirstClose).isEqualTo(1);
        assertThat(inserted).hasSize(1);
        assertThat(sink.hasPending()).isFalse();
    }

    @Test
    void aClosedSinkRefusesFurtherWrites() {
        AiEventSink sink = newSink();
        sink.close();

        int seq = sink.writeUser("late", null);

        assertThat(seq).isEqualTo(SEED_SEQ);
        assertThat(inserted).isEmpty();
        verify(eventRepository, never()).insert(any(RmqAiEvent.class));
    }

    @Test
    void aPersistenceFailureIsLoggedAndSwallowedButDuplicateKeyEscapes() {
        AiEventSink firstSink = newSink();
        doThrow(new RuntimeException("db gone")).when(eventRepository).insert(any(RmqAiEvent.class));
        firstSink.writeUser("hello", null);
        assertThat(firstSink.highWaterSeq()).isEqualTo(SEED_SEQ + 1);

        AiEventSink secondSink = newSink();
        org.springframework.dao.DuplicateKeyException duplicate =
                new org.springframework.dao.DuplicateKeyException("uk_ai_event_conversation_seq");
        doThrow(duplicate).when(eventRepository).insert(any(RmqAiEvent.class));
        assertThatThrownBy(() -> secondSink.writeUser("hello", null))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void aFailingLivePublisherNeverFailsTheWrite() {
        AiEventSink sink = new AiEventSink(CONVERSATION, RUN, TURN, SEED_SEQ, eventRepository,
                conversationRepository, new AgentEventProjector(RUN), new ObjectMapper(),
                (seq, event) -> { throw new RuntimeException("observer gone"); }, null);

        int seq = sink.writeUser("hello", null);

        assertThat(seq).isEqualTo(SEED_SEQ + 1);
        assertThat(inserted).hasSize(1);
    }

    @Test
    void aFailingCacheRefreshNeverFailsTheWrite() {
        doThrow(new RuntimeException("cache row locked")).when(conversationRepository)
                .update(any(org.apache.rocketmq.studio.persistence.entity.RmqAiConversation.class));
        AiEventSink sink = newSink();

        int seq = sink.writeUser("hello", null);

        assertThat(seq).isEqualTo(SEED_SEQ + 1);
        assertThat(inserted).hasSize(1);
    }

    @Test
    void emitErrorPublishesTheLiveTwinAtThePersistedSeq() {
        AiEventSink sink = newSink();

        int seq = sink.emitError("boom", "message", "hint");

        assertThat(seq).isEqualTo(SEED_SEQ + 1);
        assertThat(published).hasSize(1);
        assertThat(published.get(0)[0]).isEqualTo((long) seq);
    }

    @Test
    void seedSeqIsClampedAtZeroForNegativeSeeds() {
        AiEventSink sink = new AiEventSink(CONVERSATION, RUN, TURN, -5, eventRepository,
                conversationRepository, new AgentEventProjector(RUN), new ObjectMapper(), null, null);

        int seq = sink.writeUser("hello", null);

        assertThat(seq).isEqualTo(1);
        assertThat(inserted.get(0).getSeq()).isEqualTo(1);
    }

    @Test
    void writeTimelineRejectsNullEvents() {
        AiEventSink sink = newSink();

        assertThatThrownBy(() -> sink.writeTimeline(null))
                .isInstanceOf(NullPointerException.class);
        verify(eventRepository, times(0)).insert(any(RmqAiEvent.class));
    }
}
