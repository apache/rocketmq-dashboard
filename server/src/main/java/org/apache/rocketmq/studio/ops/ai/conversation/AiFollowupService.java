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
import org.apache.rocketmq.studio.ops.ai.AgentProviderRegistry;
import org.apache.rocketmq.studio.ops.ai.LlmConfigService;
import org.apache.rocketmq.studio.ops.ai.LlmConfigVO;
import org.apache.rocketmq.studio.ops.ai.OpenAiCompatibleLlmClient;
import org.apache.rocketmq.studio.ops.ai.conversation.event.TimelineEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiConversation;
import org.apache.rocketmq.studio.persistence.entity.RmqAiEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Suggests the next questions an operator is likely to ask, once a run has finished.
 *
 * <h2>Why a separate model call and not part of the run</h2>
 * The suggestions are Studio's own chrome, not the answer: folding them into the agent's system
 * prompt would spend the answer's tokens on them, put them through the tool loop, and make every
 * transcript carry three extra lines nobody asked for. A small completion over the last question
 * and answer is cheap, and its failure mode is exactly the right one — no suggestions.
 *
 * <h2>Failure is silence</h2>
 * Every failure path returns an empty list with a warning in the log, never an error status: the
 * caller renders chips from the result, and an operator who never sees a chip loses nothing they
 * were promised. A suggestion endpoint that can turn a finished conversation into an error banner
 * would be worse than no endpoint.
 */
@Slf4j
@Component
public class AiFollowupService {

    private static final String TEMPLATE_RESOURCE = "/prompts/suggest-followups.txt";
    private static final String TEMPLATE = loadTemplate();

    private static final int MAX_FOLLOWUPS = 3;
    private static final int MAX_FOLLOWUP_CHARS = 60;
    private static final int MAX_ANSWER_CHARS = 4000;
    /**
     * Size of one backwards page over the event tail, and how many pages the question search may
     * walk. A long agent run interleaves hundreds of thinking and tool rows between the user's turn
     * and the final answer, so a small window silently loses the question and the whole suggestion
     * degrades to "nothing to suggest" — indistinguishable from a real empty answer. 3 × 200 covers
     * the longest run seen so far and reuses the timeline endpoint's per-page bound.
     */
    private static final int TAIL_EVENTS = 200;
    private static final int TAIL_PAGES = 3;

    private final AiConversationService conversationService;
    private final AiEventRepository eventRepository;
    private final LlmConfigService llmConfigService;
    private final OpenAiCompatibleLlmClient llmClient;
    private final AgentProviderRegistry agentProviders;
    private final ObjectMapper objectMapper;

    public AiFollowupService(AiConversationService conversationService,
                             AiEventRepository eventRepository,
                             LlmConfigService llmConfigService,
                             OpenAiCompatibleLlmClient llmClient,
                             AgentProviderRegistry agentProviders,
                             ObjectMapper objectMapper) {
        this.conversationService = conversationService;
        this.eventRepository = eventRepository;
        this.llmConfigService = llmConfigService;
        this.llmClient = llmClient;
        this.agentProviders = agentProviders;
        this.objectMapper = objectMapper;
    }

    /**
     * @return up to {@value #MAX_FOLLOWUPS} follow-up questions for the conversation's latest
     *     question/answer pair, or an empty list when there is nothing to suggest from or the
     *     suggestion call failed
     */
    public List<String> suggest(Long conversationId) {
        RmqAiConversation conversation = conversationService.requireOwned(
                conversationId, AiConversationService.currentOwner());
        LlmConfigVO config = llmConfigService.getConfig();
        if (config == null || !config.isReady()) {
            return List.of();
        }
        String question;
        String answer;
        try {
            int maxSeq = eventRepository.maxSeq(conversationId);
            if (maxSeq <= 0) {
                return List.of();
            }
            // Walk the tail backwards in pages: the final answer is the last text row of the newest
            // page, while the question it answers can sit hundreds of rows earlier — a long agent
            // run interleaves that many thinking and tool rows between the two. A single small
            // window silently loses the question and degrades every suggestion to "nothing to
            // suggest", which is indistinguishable from a genuine empty answer.
            question = null;
            answer = null;
            for (int page = 0; page < TAIL_PAGES; page += 1) {
                int from = Math.max(0, maxSeq - TAIL_EVENTS * (page + 1));
                List<RmqAiEvent> rows = eventRepository.findByConversationIdAfterSeq(
                        conversationId, from, TAIL_EVENTS);
                if (rows.isEmpty()) {
                    break;
                }
                for (RmqAiEvent row : rows) {
                    TimelineEvent event = AiEventCodec.read(objectMapper, row).orElse(null);
                    if (event instanceof TimelineEvent.User user) {
                        question = user.text();
                    } else if (page == 0 && event instanceof TimelineEvent.Text text) {
                        answer = text.text();
                    }
                }
                if (question != null) {
                    break;
                }
            }
        } catch (RuntimeException exception) {
            log.warn("could not read the tail of conversation {} for follow-ups: {}",
                    conversationId, exception.toString());
            return List.of();
        }
        if (!StringUtils.hasText(question) || !StringUtils.hasText(answer)) {
            return List.of();
        }
        String prompt = TEMPLATE.formatted(question.trim(), abbreviate(answer.trim()));
        StringBuilder accumulated = new StringBuilder();
        try {
            if (isHttpEngine(conversation.getEngine())) {
                llmClient.stream(config, prompt, null, accumulated::append);
            } else {
                agentProviders.forEngine(conversation.getEngine())
                        .stream(config, prompt, null, accumulated::append);
            }
        } catch (RuntimeException exception) {
            log.warn("follow-up suggestion failed for conversation {}: {}",
                    conversationId, exception.toString());
            return List.of();
        }
        return parse(accumulated.toString());
    }

    /**
     * One suggestion per line, decoration stripped. Models number or bullet their lists despite
     * being told not to, and a leading "1." would be sent as part of the next question when the
     * operator clicks the chip.
     */
    static List<String> parse(String raw) {
        List<String> followups = new ArrayList<>();
        for (String line : raw.split("\n")) {
            // Non-ASCII decoration (full-width colon, CJK enumeration comma, curly quotes, bullets)
            // is spelled as escapes: checkstyle forbids literal CJK characters in Java sources.
            String cleaned = line.trim()
                    .replaceFirst("^[-*\u2022\u00B7\u3001]+\\s*", "")
                    .replaceFirst("^\\d+[.)\u3001]\\s*", "")
                    .replaceFirst("^[:\uFF1A]\\s*", "")
                    .replaceAll("^[\"'\u201C\u201D]+|[\"'\u201C\u201D]+$", "")
                    .trim();
            if (!StringUtils.hasText(cleaned) || cleaned.length() > MAX_FOLLOWUP_CHARS
                    || followups.contains(cleaned)) {
                continue;
            }
            followups.add(cleaned);
            if (followups.size() == MAX_FOLLOWUPS) {
                break;
            }
        }
        return followups;
    }

    private static String abbreviate(String text) {
        return text.length() <= MAX_ANSWER_CHARS
                ? text
                : text.substring(0, MAX_ANSWER_CHARS) + "...";
    }

    private static boolean isHttpEngine(String engine) {
        return !StringUtils.hasText(engine)
                || LlmConfigVO.ENGINE_HTTP.equals(engine.trim().toLowerCase(Locale.ROOT));
    }

    private static String loadTemplate() {
        try (InputStream input = AiFollowupService.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException(TEMPLATE_RESOURCE.substring(1) + " is missing on classpath");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load " + TEMPLATE_RESOURCE, exception);
        }
    }
}
