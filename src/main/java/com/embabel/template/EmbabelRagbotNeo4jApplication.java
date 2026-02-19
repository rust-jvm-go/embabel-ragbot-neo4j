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
package com.embabel.template;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Embabel RAGBot Neo4j application.
 * <p>
 * <b>How Embabel Bootstraps:</b> {@code @SpringBootApplication} triggers component scanning
 * and auto-configuration. The {@code embabel-agent-starter} dependency contributes
 * auto-configuration classes that:
 * <ol>
 *   <li>Create and configure the {@link com.embabel.agent.core.AgentPlatform} — the central
 *       coordination hub that manages agent registration, planning, and execution.</li>
 *   <li>Scan for {@code @Agent}-annotated classes and register them with the platform.</li>
 *   <li>Configure LLM providers (Ollama, OpenAI, Anthropic) based on available dependencies
 *       and environment variables.</li>
 *   <li>Set up Spring Shell integration with dynamic commands ({@code x}, {@code execute},
 *       {@code chat}) for interactive agent invocation.</li>
 *   <li>Make the {@link com.embabel.agent.api.common.Ai} interface available for injection
 *       into any Spring bean.</li>
 * </ol>
 *
 * @see com.embabel.agent.core.AgentPlatform
 */
@SpringBootApplication
class EmbabelRagbotNeo4jApplication {

    /** Default constructor. */
    EmbabelRagbotNeo4jApplication() {
    }

    /**
     * Application entry point.
     *
     * @param args command-line arguments forwarded to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(EmbabelRagbotNeo4jApplication.class, args);
    }
}
