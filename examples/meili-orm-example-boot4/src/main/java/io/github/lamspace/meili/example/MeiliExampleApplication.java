/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package io.github.lamspace.meili.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Demo startup shell for Boot 4.0.3.
 *
 * <p>This package is the auto-configuration root: entity scanning starts here and finds
 * example-common's {@code domain} subpackage; component scanning finds the {@code web}/{@code config}
 * subpackages. The shell holds zero business code — the two demos differ only in the BOM and this
 * one startup class.
 */
@SpringBootApplication
public class MeiliExampleApplication {

    /**
     * The startup class is constructed by the framework; all entry logic lives in {@link #main(String[])}.
     */
    public MeiliExampleApplication() {
    }

    /**
     * Process entry point.
     *
     * @param args command-line arguments passed through to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(MeiliExampleApplication.class, args);
    }
}
