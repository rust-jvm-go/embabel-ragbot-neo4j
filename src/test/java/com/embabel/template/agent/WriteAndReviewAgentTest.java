package com.embabel.template.agent;

import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.test.unit.FakeOperationContext;
import com.embabel.agent.test.unit.FakePromptRunner;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link WriteAndReviewAgent} using Embabel's fake testing infrastructure.
 * <p>
 * <b>Embabel Unit Testing Approach:</b> Embabel provides {@link FakeOperationContext} and
 * {@link FakePromptRunner} for testing individual {@code @Action} methods in isolation,
 * without calling actual LLMs or starting the Spring context. This enables:
 * <ul>
 *   <li><b>Prompt verification</b> — inspect the exact prompts sent to the LLM</li>
 *   <li><b>Deterministic responses</b> — pre-configure expected LLM responses</li>
 *   <li><b>Hyperparameter inspection</b> — verify LLM options (temperature, model, etc.)</li>
 *   <li><b>Fast execution</b> — no network calls, no Spring Boot startup</li>
 * </ul>
 * <p>
 * <b>Pattern:</b>
 * <ol>
 *   <li>Create a {@code FakeOperationContext} via {@code FakeOperationContext.create()}</li>
 *   <li>Queue expected responses with {@code context.expectResponse(...)}</li>
 *   <li>Construct the agent directly (it's a plain Java class)</li>
 *   <li>Call the action method, passing {@code context.ai()} as the {@code Ai} parameter</li>
 *   <li>Inspect {@code context.getLlmInvocations()} to verify prompts and parameters</li>
 * </ol>
 *
 * @see FakeOperationContext
 * @see FakePromptRunner
 */
class WriteAndReviewAgentTest {

    /**
     * Tests that the {@code craftStory} action produces a prompt containing the user's input.
     * <p>
     * The {@code FakeOperationContext} intercepts the LLM call and returns the pre-configured
     * {@code Story} object. We then verify the prompt content to ensure the user's topic
     * ("knight") was included in the prompt sent to the LLM.
     */
    @Test
    void testWriteAndReviewAgent() {
        // Create a fake context that intercepts LLM calls instead of making real ones
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();
        // Queue the response that the fake LLM will return for createObject()
        context.expectResponse(new WriteAndReviewAgent.Story("One upon a time Sir Galahad . . "));

        // Construct the agent directly — no Spring context needed for unit tests
        var agent = new WriteAndReviewAgent(200, 400);
        // Call the action method with the fake Ai instance
        var story = agent.craftStory(new UserInput("Tell me a story about a brave knight", Instant.now()), context.ai());

        // Verify the prompt sent to the LLM contains the expected user input
        var prompt = promptRunner.getLlmInvocations().getFirst().getMessages().getFirst().getContent();
        assertTrue(prompt.contains("knight"), "Expected prompt to contain 'knight'");

    }

    /**
     * Tests that the {@code reviewStory} action produces a prompt containing both
     * the user's original input and the story text for review.
     */
    @Test
    void testReview() {
        var agent = new WriteAndReviewAgent(200, 400);
        var userInput = new UserInput("Tell me a story about a brave knight", Instant.now());
        var story = new WriteAndReviewAgent.Story("Once upon a time, Sir Galahad...");
        var context = FakeOperationContext.create();
        // Queue the text response that generateText() will return
        context.expectResponse("A thrilling tale of bravery and adventure!");
        var review = agent.reviewStory(userInput, story, context.ai());
        // Inspect the LLM invocation to verify prompt content
        var llmInvocation = context.getLlmInvocations().getFirst();
        var prompt = llmInvocation.getMessages().getFirst().getContent();
        assertTrue(prompt.contains("knight"), "Expected prompt to contain 'knight'");
        assertTrue(prompt.contains("review"), "Expected prompt to contain 'review'");
    }

}