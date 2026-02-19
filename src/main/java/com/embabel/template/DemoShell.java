package com.embabel.template;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.template.agent.WriteAndReviewAgent;
import com.embabel.template.injected.InjectedDemo;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

/**
 * Spring Shell commands demonstrating different ways to invoke Embabel agents.
 * <p>
 * <b>Key Embabel Concept — Execution Modes:</b> Embabel supports three execution modes:
 * <ul>
 *   <li><b>Focused mode</b> ({@code demo} command below): User code programmatically invokes
 *       a specific agent via {@link AgentInvocation}. The platform runs the agent's GOAP planner
 *       to build and execute a plan toward the requested output type. This is the most common
 *       mode in production applications (e.g., invoked from a REST controller or event handler).</li>
 *   <li><b>Closed mode</b> (the built-in {@code x "prompt"} command): The platform uses an LLM
 *       to classify user input and select the most appropriate registered agent. Only actions
 *       within the selected agent are considered.</li>
 *   <li><b>Open mode</b>: The platform selects a <em>goal</em> (not an agent) and builds a
 *       custom agent from all available actions across all registered agents to achieve it.
 *       Most powerful but least deterministic.</li>
 * </ul>
 * <p>
 * <b>@ShellComponent:</b> A Spring Shell stereotype. Methods annotated with {@code @ShellMethod}
 * become interactive CLI commands. Embabel also registers its own dynamic commands ({@code x},
 * {@code execute}, {@code chat}) for agent invocation.
 *
 * @see com.embabel.agent.api.invocation.AgentInvocation
 * @see com.embabel.agent.core.AgentPlatform
 */
@ShellComponent
record DemoShell(InjectedDemo injectedDemo, AgentPlatform agentPlatform) {

    /**
     * Demonstrates <b>Focused mode</b> agent invocation — the most common pattern
     * in production applications.
     * <p>
     * {@link AgentInvocation#create(AgentPlatform, Class)} creates an invocation targeting
     * the goal output type ({@code ReviewedStory.class}). The platform finds the agent
     * that can produce this type, runs the GOAP planner, and executes the resulting plan.
     * <p>
     * The result implements {@link com.embabel.agent.domain.library.HasContent}, so
     * {@code getContent()} returns a formatted string suitable for display.
     */
    @ShellMethod("Demo")
    String demo() {
        // Focused mode: Programmatically invoke a specific agent by requesting its goal output type.
        // The GOAP planner determines the action sequence: craftStory -> reviewStory.
        var reviewedStory = AgentInvocation
                .create(agentPlatform, WriteAndReviewAgent.ReviewedStory.class)
                .invoke(new UserInput("Tell me a story about caterpillars"));
        return reviewedStory.getContent();
    }

    /**
     * Demonstrates using Embabel's {@link com.embabel.agent.api.common.Ai} interface
     * outside of an agent context, via the injected {@link InjectedDemo} component.
     * <p>
     * This shows that LLM operations are not limited to {@code @Agent} classes —
     * any Spring bean can use the {@code Ai} interface for ad-hoc LLM calls.
     */
    @ShellMethod("Invent an animal")
    String animal() {
        return injectedDemo.inventAnimal().toString();
    }
}
