/**
 * Copyright 2025 SkillTree
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package skills.controller

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import skills.auth.openai.GenDescRequest
import skills.auth.openai.OpenAIAssistantService
import skills.auth.openai.OpenAIService
import skills.controller.exceptions.SkillsValidator

@RestController
@RequestMapping("/openai")
@Slf4j
class OpenAiController {

    @Autowired
    OpenAIService openAIService

    @Autowired
    OpenAIAssistantService openAIAssistantService

    @Value("classpath:SkillTreeConcepts.pdf")
    Resource skillTreeConceptsResourceFile;

    @PostMapping(value = "/stream/description", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<String> generateDescriptionAndStream(@RequestBody GenDescRequest genDescRequest) {
        SkillsValidator.isNotBlank(genDescRequest.instructions, "genDescRequest.instructions")
        return openAIService.streamCompletions(genDescRequest.instructions)
    }

    @GetMapping("/models")
    OpenAIService.AvailableModels getModels() {
        return openAIService.getAvailableModels()
    }

    @PostMapping('/uploadAndAsk')
    Mono<String> uploadAndAsk(@RequestParam("files") MultipartFile[] multipartFiles,
                              @RequestParam('question') String question) {
        List<File> filesToUpload = [skillTreeConceptsResourceFile.getFile()]
        for (MultipartFile multipartFile : multipartFiles) {
            log.info("received file [${multipartFile.originalFilename}]")
            def file = new File(System.getProperty('java.io.tmpdir'), multipartFile.originalFilename)
            multipartFile.transferTo(file)
            filesToUpload.add(file);
        }

        // Sequential flow
        def vectorStoreId = openAIAssistantService.uploadFilesToVectorStore(filesToUpload)

        def generateProjectResponse = openAIAssistantService.createAssistant(
                'DocQA',
                'You are an assistant that answers questions about the uploaded document.',
                'gpt-4o-mini',
                vectorStoreId
        ).flatMap { assistantId ->
            openAIAssistantService.createThread()
                .flatMap { threadId ->
                    openAIAssistantService.addUserMessage(threadId, question)
                        .flatMap {
                            openAIAssistantService.runAssistant(threadId, assistantId)
                                .flatMap { runId ->
                                    openAIAssistantService.pollRunUntilComplete(threadId, runId)
                                            .then(openAIAssistantService.getAssistantResponse(threadId))
                                }
                        }
                }
        }
        return generateProjectResponse
    }
}

