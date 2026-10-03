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
package org.apache.rocketmq.studio.ops.ai.conversation.agent;

import org.springframework.util.StringUtils;

/**
 * Recognises the one failure mode that {@code claude --resume} has and that a retry can fix: the
 * session the conversation remembers no longer exists on disk.
 *
 * <p>{@code RmqctlWorkspace} keeps {@code HOME} and the working directory stable per conversation
 * precisely so that {@code $HOME/.claude/projects/<cwd-hash>/<session-id>.jsonl} survives between
 * turns. It cannot make it survive everything: the default workspace lives under {@code /tmp}, so a
 * container restart wipes it, and {@code conversation.runtime_session_id} then points at a session
 * the CLI will never find again.
 *
 * <h2>What the CLI actually does (measured, not assumed)</h2>
 * A run started with a stale {@code --resume} id exits 1 with exactly one line on stderr —
 * {@code No conversation found with session ID: <id>\n} — and a single {@code result} frame on stdout
 * whose {@code subtype} is {@code error_during_execution}, with {@code num_turns} 0, the bogus id
 * echoed back and an {@code errors[]} array. Both signals are checked, because either one alone is
 * fragile: the stderr text is a human-readable string that a CLI upgrade may reword, and
 * {@code error_during_execution} is also used for failures that have nothing to do with resume.
 *
 * <h2>The recovery contract</h2>
 * When {@link #shouldRetryWithoutResume} returns true the caller must, exactly once:
 * <ol>
 *   <li>re-run the same turn <em>without</em> {@code --resume};</li>
 *   <li>clear {@code conversation.runtime_session_id}, so the next turn does not repeat the failure
 *       and the new session id from the retry's {@code result} frame becomes the one that is
 *       remembered.</li>
 * </ol>
 * Losing the earlier turns' context is the price; without the retry the conversation is permanently
 * broken, because every subsequent turn would resume the same missing id.
 *
 * <p>The signal is detected where the exit code and the stderr are still in scope — the provider that
 * spawned the CLI — and travels to the caller as {@link #RESUME_LOST_CODE} on a
 * {@code LlmGatewayException}, because the frames alone cannot express it: the command that has to
 * drop {@code --resume} is the provider's to build.
 */
public final class ResumeRecovery {

    /**
     * The error code a provider reports when a run could not resume the session its conversation
     * remembers. The caller that sees it owns the recovery: retry the turn once without
     * {@code --resume}, after clearing {@code conversation.runtime_session_id}.
     */
    public static final String RESUME_LOST_CODE = "llm.provider.resume_lost";

    /** The CLI's stderr line, matched as a prefix of the first non-blank content. */
    static final String SESSION_NOT_FOUND_PREFIX = "No conversation found with session ID:";

    /** The {@code result} frame subtype the CLI reports for a failed resume. */
    static final String ERROR_DURING_EXECUTION = "error_during_execution";

    private ResumeRecovery() {
    }

    /**
     * The raw signal, exactly as specified: a non-zero exit plus either the session-not-found stderr
     * line or the {@code error_during_execution} result subtype.
     *
     * <p>Callers that did not pass {@code --resume} should use
     * {@link #shouldRetryWithoutResume(boolean, int, String, String)} instead — retrying a run that
     * never resumed anything just repeats it.
     */
    public static boolean isLostResumeSignal(int exitCode, String resultSubtype, String stderr) {
        if (exitCode == 0) {
            return false;
        }
        if (ERROR_DURING_EXECUTION.equals(resultSubtype)) {
            return true;
        }
        return StringUtils.hasText(stderr) && stderr.stripLeading().startsWith(SESSION_NOT_FOUND_PREFIX);
    }

    /**
     * Whether the run should be retried once without {@code --resume}.
     *
     * @param resumeRequested true when this run was started with {@code --resume}; the recovery is
     *     meaningless otherwise
     */
    public static boolean shouldRetryWithoutResume(boolean resumeRequested, int exitCode,
                                                   String resultSubtype, String stderr) {
        return resumeRequested && isLostResumeSignal(exitCode, resultSubtype, stderr);
    }
}
