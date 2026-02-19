package com.embabel.template.agent;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the complete {@link WriteAndReviewAgent} workflow.
 * <p>
 * <b>Embabel Integration Testing Approach:</b> Extends {@link EmbabelMockitoIntegrationTest},
 * which provides a full Spring Boot test context with:
 * <ul>
 *   <li>A real {@link com.embabel.agent.core.AgentPlatform} with all agents registered</li>
 *   <li>A mocked {@code LlmOperations} layer — LLM calls are intercepted, not sent to real models</li>
 *   <li>A mocked {@code ModelProvider} — no actual model connections needed</li>
 *   <li>Shell disabled via {@code @TestPropertySource} to avoid interactive mode</li>
 * </ul>
 * <p>
 * <b>How it differs from unit tests:</b> Unit tests (using {@code FakeOperationContext}) test
 * individual {@code @Action} methods in isolation. Integration tests exercise the <em>complete
 * agent workflow</em> including GOAP planning, action sequencing, and the full Spring context.
 * The platform determines the execution plan (craftStory → reviewStory) automatically.
 * <p>
 * <b>Pattern:</b>
 * <ol>
 *   <li>Stub LLM responses with {@code whenCreateObject()} / {@code whenGenerateText()}</li>
 *   <li>Invoke the agent via {@link AgentInvocation} (Focused mode)</li>
 *   <li>Assert on the result</li>
 *   <li>Verify LLM calls and hyperparameters with {@code verifyCreateObjectMatching()} /
 *       {@code verifyGenerateTextMatching()}</li>
 *   <li>Call {@code verifyNoMoreInteractions()} to ensure no unexpected LLM calls</li>
 * </ol>
 *
 * @see EmbabelMockitoIntegrationTest
 * @see com.embabel.agent.api.invocation.AgentInvocation
 */
class WriteAndReviewAgentIntegrationTest extends EmbabelMockitoIntegrationTest {

    @BeforeAll
    static void setUp() {
        // Disable interactive shell mode for headless test execution
        System.setProperty("embabel.agent.shell.interactive.enabled", "false");
    }

    /**
     * Tests the complete agent workflow: craftStory → reviewStory.
     * <p>
     * The GOAP planner automatically determines that {@code craftStory} must execute first
     * (it produces {@code Story}), followed by {@code reviewStory} (which consumes {@code Story}
     * and produces {@code ReviewedStory}). This test verifies:
     * <ul>
     *   <li>The plan executes correctly end-to-end</li>
     *   <li>The story content appears in the final output</li>
     *   <li>The craftStory action uses temperature 0.7 and no tool groups</li>
     *   <li>Both LLM calls receive the expected prompts</li>
     *   <li>No unexpected LLM interactions occur</li>
     * </ul>
     */
    @Test
    void shouldExecuteCompleteWorkflow() {
        var input = new UserInput("Write about artificial intelligence");

        var story = new WriteAndReviewAgent.Story("AI will transform our world...");
        var reviewedStory = new WriteAndReviewAgent.ReviewedStory(story, "Excellent exploration of AI themes.", Personas.REVIEWER);

        // Stub the createObject() call in craftStory — matches on prompt content and output type
        whenCreateObject(prompt -> prompt.contains("Craft a short story"), WriteAndReviewAgent.Story.class)
                .thenReturn(story);

        // Stub the generateText() call in reviewStory
        whenGenerateText(prompt -> prompt.contains("You will be given a short story to review"))
                .thenReturn(reviewedStory.review());

        // Invoke the agent in Focused mode — the platform runs the GOAP planner
        // and executes: craftStory -> reviewStory
        var invocation = AgentInvocation.create(agentPlatform, WriteAndReviewAgent.ReviewedStory.class);
        var reviewedStoryResult = invocation.invoke(input);

        // Assert on the final result
        assertNotNull(reviewedStoryResult);
        assertTrue(reviewedStoryResult.getContent().contains(story.text()),
                "Expected story content to be present: " + reviewedStoryResult.getContent());
        assertEquals(reviewedStory, reviewedStoryResult,
                "Expected review to match: " + reviewedStoryResult);

        // Verify LLM hyperparameters: craftStory should use temperature 0.7 and no tools
        verifyCreateObjectMatching(prompt -> prompt.contains("Craft a short story"), WriteAndReviewAgent.Story.class,
                llm -> llm.getLlm().getTemperature() == 0.7 && llm.getToolGroups().isEmpty());
        // Verify the review prompt was sent correctly
        verifyGenerateTextMatching(prompt -> prompt.contains("You will be given a short story to review"));
        // Ensure no unexpected LLM calls were made
        verifyNoMoreInteractions();
    }
}
