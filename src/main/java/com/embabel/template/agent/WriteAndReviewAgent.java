/*
 * Copyright 2024-2025 Embabel Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.template.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Export;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.domain.library.HasContent;
import com.embabel.agent.prompt.persona.Persona;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.core.types.Timestamped;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Personas used by the {@link WriteAndReviewAgent} to shape LLM behavior.
 * <p>
 * In Embabel, a <b>Persona</b> is a {@link com.embabel.common.ai.prompt.PromptContributor}
 * that adds personality context to the system prompt sent to the LLM. This guides the
 * model's tone, style, and focus without changing the core prompt logic.
 * <p>
 * Two persona styles are demonstrated here:
 * <ul>
 *   <li>{@link RoleGoalBackstory} — CrewAI-style persona with role/goal/backstory fields.
 *       Built using a fluent Java builder: {@code withRole(...).andGoal(...).andBackstory(...)}.</li>
 *   <li>{@link Persona} — Embabel's native persona with name/persona/voice/objective fields.
 *       Created via the static factory: {@code Persona.create(...)}.</li>
 * </ul>
 * Both implement {@code PromptContributor}, so they can be passed to
 * {@code PromptRunner.withPromptContributor()} to inject persona context into any LLM call.
 *
 * @see com.embabel.agent.prompt.persona.RoleGoalBackstory
 * @see com.embabel.agent.prompt.persona.Persona
 * @see com.embabel.common.ai.prompt.PromptContributor
 */
abstract class Personas {

    // RoleGoalBackstory: A CrewAI-compatible persona style included in Embabel for users
    // migrating from CrewAI. It contributes "Role: / Goal: / Backstory:" to the system prompt.
    static final RoleGoalBackstory WRITER = RoleGoalBackstory
            .withRole("Creative Storyteller")
            .andGoal("Write engaging and imaginative stories")
            .andBackstory("Has a PhD in French literature; used to work in a circus");

    // Persona: Embabel's native persona style contributing "You are [name]. Your persona: ...
    // Your objective is ... Your voice: ..." to the system prompt.
    static final Persona REVIEWER = Persona.create(
            "Media Book Review",
            "New York Times Book Reviewer",
            "Professional and insightful",
            "Help guide readers toward good stories"
    );
}

/**
 * An Embabel agent that generates a creative story and then reviews it.
 * <p>
 * <b>Embabel @Agent Annotation:</b> The {@link Agent} annotation marks this class as an
 * Embabel agent — a Spring stereotype that is automatically discovered via classpath scanning
 * and registered with the {@link com.embabel.agent.core.AgentPlatform}. The {@code description}
 * field is critical: it is used by the platform to select this agent when processing user input
 * in <em>Closed mode</em> (where the LLM chooses the best agent based on descriptions).
 * <p>
 * <b>GOAP Planning:</b> By default, agents use Goal Oriented Action Planning (GOAP) — a
 * non-LLM AI algorithm that dynamically creates execution plans by analyzing the preconditions
 * and effects (input/output types) of each {@link Action} method. The planner determines the
 * optimal sequence of actions to achieve the declared {@link AchievesGoal}. This means:
 * <ul>
 *   <li>The framework infers that {@code craftStory} must run before {@code reviewStory}
 *       because {@code reviewStory} requires a {@link Story} parameter.</li>
 *   <li>Both actions require {@link UserInput}, which is provided as the initial input.</li>
 *   <li>The plan is re-evaluated after each action (OODA loop), allowing adaptation.</li>
 * </ul>
 * <p>
 * <b>Domain Model:</b> The strongly-typed records {@link Story} and {@link ReviewedStory}
 * form the domain model. They flow through the agent's actions as inputs and outputs,
 * enabling compile-time safety and full IDE refactoring support — no "magic maps".
 *
 * @see com.embabel.agent.api.annotation.Agent
 * @see com.embabel.agent.api.annotation.Action
 * @see com.embabel.agent.api.annotation.AchievesGoal
 */
@Agent(description = "Generate a story based on user input and review it")
public class WriteAndReviewAgent {

    /**
     * Domain object representing a generated story.
     * <p>
     * In Embabel, domain objects are the typed data that flows between actions on the
     * agent's <b>blackboard</b> (an in-memory store of objects produced during execution).
     * When {@code craftStory} returns a {@code Story}, it is placed on the blackboard,
     * satisfying the precondition for {@code reviewStory} which requires a {@code Story} parameter.
     *
     * @param text the story content generated by the LLM
     */
    public record Story(String text) {
    }

    /**
     * Domain object representing the final output: a story with its review.
     * <p>
     * Implements {@link HasContent} so the result can be rendered as text by the shell,
     * and {@link Timestamped} to record when the review was produced.
     * This is the <b>goal output type</b> — the type requested when invoking the agent
     * programmatically via {@code AgentInvocation.create(platform, ReviewedStory.class)}.
     *
     * @param story the original story that was reviewed
     * @param review the text of the review produced by the reviewer persona
     * @param reviewer the persona that performed the review
     */
    public record ReviewedStory(
            Story story,
            String review,
            Persona reviewer
    ) implements HasContent, Timestamped {

        @Override
        @NonNull
        public Instant getTimestamp() {
            return Instant.now();
        }

        @Override
        @NonNull
        public String getContent() {
            return String.format("""
                            # Story
                            %s
                            
                            # Review
                            %s
                            
                            # Reviewer
                            %s, %s
                            """,
                    story.text(),
                    review,
                    reviewer.getName(),
                    getTimestamp().atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy"))
            ).trim();
        }
    }

    private final int storyWordCount;
    private final int reviewWordCount;

    /**
     * Constructor with Spring {@link Value} injection for configurable word counts.
     * <p>
     * Because {@code @Agent} is a Spring stereotype, this class is a Spring bean.
     * Spring injects constructor parameters, including {@code @Value} properties
     * with defaults. This allows runtime configuration via application.properties/yml
     * or command-line arguments (e.g., {@code --storyWordCount=200}).
     *
     * @param storyWordCount maximum word count for generated stories (default: 100)
     * @param reviewWordCount maximum word count for reviews (default: 100)
     */
    WriteAndReviewAgent(
            @Value("${storyWordCount:100}") int storyWordCount,
            @Value("${reviewWordCount:100}") int reviewWordCount
    ) {
        this.storyWordCount = storyWordCount;
        this.reviewWordCount = reviewWordCount;
    }

    /**
     * Reviews the generated story using a reviewer persona.
     * <p>
     * <b>@AchievesGoal:</b> Marks this action as the <em>terminal action</em> — completing it
     * means the agent's goal is achieved and execution ends. The {@code description} is used
     * by the platform to match user intent to goals in <em>Open mode</em>. The {@code tags}
     * and {@code examples} fields help with goal classification and selection.
     * <p>
     * <b>@Export:</b> Exposes this goal as a remotely-callable tool named
     * {@code "writeAndReviewStory"}, enabling A2A (Agent-to-Agent) protocol integration
     * and MCP server exposure with zero additional code.
     * <p>
     * <b>Action Parameters:</b> The GOAP planner infers preconditions from method parameters:
     * {@code UserInput} (provided at invocation) and {@code Story} (produced by {@code craftStory}).
     * The {@link Ai} parameter is a special framework-injected context giving access to LLM operations.
     * <p>
     * <b>PromptRunner:</b> {@code ai.withAutoLlm()} returns a {@link com.embabel.agent.api.common.PromptRunner}
     * configured with automatic model selection. The fluent API chains
     * {@code .withPromptContributor()} to inject the reviewer persona into the system prompt,
     * then {@code .generateText()} to produce a plain text review.
     */
    @AchievesGoal(
            description = "The story has been crafted and reviewed by a book reviewer",
            tags = {"creative-writing", "story-review"},
            examples = {"Write and review a story about a robot learning to paint",
                    "Tell me a story about caterpillars and review it"},
            export = @Export(remote = true, name = "writeAndReviewStory"))
    @Action(description = "Review the generated story using a book reviewer persona")
    ReviewedStory reviewStory(UserInput userInput, Story story, Ai ai) {
        // withAutoLlm(): Uses automatic model selection criteria — the platform may consider
        // prompt complexity and available tools to choose the best model.
        // withPromptContributor(): Injects the REVIEWER persona into the system prompt.
        // generateText(): Sends the prompt and returns plain text (vs. createObject() for typed output).
        var review = ai
                .withAutoLlm()
                .withPromptContributor(Personas.REVIEWER)
                .generateText(String.format("""
                                You will be given a short story to review.
                                Review it in %d words or less.
                                Consider whether or not the story is engaging, imaginative, and well-written.
                                Also consider whether the story is appropriate given the original user input.
                                
                                # Story
                                %s
                                
                                # User input that inspired the story
                                %s
                                """,
                        reviewWordCount,
                        story.text(),
                        userInput.getContent()
                ).trim());

        return new ReviewedStory(
                story,
                review,
                Personas.REVIEWER
        );
    }

    /**
     * Crafts a creative story based on user input.
     * <p>
     * <b>@Action:</b> Marks this method as an action the GOAP planner can include in its plan.
     * The planner infers preconditions from the parameter types ({@code UserInput}) and
     * postconditions from the return type ({@code Story}). No explicit pre/post annotations
     * are needed when data flow defines the dependencies.
     * <p>
     * <b>LLM Configuration:</b> Uses {@link LlmOptions#withAutoLlm()} with a higher temperature
     * (0.7) for more creative output. Other options include:
     * <ul>
     *   <li>{@code LlmOptions.withModel("gpt-4")} — select a specific model by name</li>
     *   <li>{@code ai.withLlmByRole("best")} — select by configured role</li>
     *   <li>{@code ai.withDefaultLlm()} — use the platform default model</li>
     *   <li>{@code ai.withFirstAvailableLlmOf("model-a", "model-b")} — fallback chain</li>
     * </ul>
     * <p>
     * <b>createObject():</b> Unlike {@code generateText()}, this method deserializes the LLM
     * response into a strongly-typed {@code Story} record. The framework handles JSON schema
     * generation, prompt construction, and response parsing automatically.
     */
    @Action(description = "Craft a creative story based on user input")
    Story craftStory(UserInput userInput, Ai ai) {
        return ai
                // withLlm(LlmOptions): Full control over model selection and hyperparameters.
                // withAutoLlm(): Automatic model selection (platform may consider prompt/tools).
                // withTemperature(0.7): Higher temperature for more creative, varied output.
                .withLlm(LlmOptions
                        .withAutoLlm()
                        .withTemperature(.7)
                )
                .withPromptContributor(Personas.WRITER)
                // createObject(): Sends the prompt to the LLM and deserializes the response
                // into the specified type (Story.class). The framework generates a JSON schema
                // from the record definition and instructs the LLM to produce conforming output.
                .createObject(String.format("""
                                Craft a short story in %d words or less.
                                The story should be engaging and imaginative.
                                Use the user's input as inspiration if possible.
                                If the user has provided a name, include it in the story.
                                
                                # User input
                                %s
                                """,
                        storyWordCount,
                        userInput.getContent()
                ).trim(), Story.class);
    }
}
