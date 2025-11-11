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

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.Canonical
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import reactor.core.publisher.Flux
import skills.auth.UserInfoService
import skills.auth.openai.GenDescRequest
import skills.auth.openai.OpenAIAssistantService
import skills.auth.openai.OpenAIService
import skills.controller.exceptions.SkillsValidator
import skills.controller.request.model.UserSettingsRequest
import skills.controller.result.model.SettingsResult
import skills.services.settings.SettingsService
import skills.storage.model.auth.User
import skills.storage.repos.UserRepo

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

    @Autowired
    SettingsService settingsService

    @Autowired
    UserInfoService userInfoService

    @Autowired
    UserRepo userRepo

    JsonSlurper jsonSlurper = new JsonSlurper()

    static final String SETTING = 'genProjSettings'
    static final String SETTING_GROUP = 'user'

    final String systemInstructions = """
You are a chatbot that will help a user create a training profile for the SkillTree application. Use the attached knowledge base via file_search.  Use SkillTreeConcepts.pdf to better understand the SkillTree Platform.  All other documents in the attached knowledge base should be used to create a coherent, validated training curriculum that can be translated into SkillTree subjects, skills, badges, etc... 

The response should be structured and scannable: Detailed descriptions that use Markdown with headers, tables, blocks and consistent formatting.  You may also and include images from the documents in the knowledge base when applicable and appropriate.  Initially your response should show the generated training profile using using markdown as SkillTree subjects, skills, badges, each with details descriptions so the user can preview it's structure.  Eventually, you will be asked to translate the training profile structure into a SkillTree specific JSON format"""

    @GetMapping("/vector_stores")
    def listVectorStores() {
        return openAIAssistantService.listVectorStores()
    }
    @GetMapping("/vector_stores/{id}")
    def getVectorStore(@PathVariable("id") String vectorId) {
        return openAIAssistantService.getVectorStore(vectorId)
    }
    @GetMapping("/vector_stores/{id}/files")
    def getVectorStoreFiles(@PathVariable("id") String vectorId) {
        return openAIAssistantService.getVectorStoreFiles(vectorId)
    }
    @GetMapping("/threads/{id}")
    def getThread(@PathVariable("id") String threadId) {
        return openAIAssistantService.getThread(threadId)
    }
    @GetMapping("/files")
    def listFiles() {
        return openAIAssistantService.listFiles()
    }

    @PostMapping(value = "/stream/description", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<String> generateDescriptionAndStream(@RequestBody GenDescRequest genDescRequest) {
        SkillsValidator.isNotBlank(genDescRequest.instructions, "genDescRequest.instructions")
        return openAIService.streamCompletions(genDescRequest.instructions)
    }

    @GetMapping("/models")
    OpenAIService.AvailableModels getModels() {
        return openAIService.getAvailableModels()
    }

    @GetMapping("/storeFiles")
    List getCurrentStoreFiles() {
        UserChatSettings userChatSettings = loadUserChatSetting()
        if (!userChatSettings?.vectorStoreId) {
            return []
        }
        def vectorFiles = openAIAssistantService.getVectorStoreFiles(userChatSettings.vectorStoreId)
        def allFilesParsed = openAIAssistantService.listFiles()

        vectorFiles.data.collect { vectorFile -> {
            def file = allFilesParsed.data.find { it.id == vectorFile.id }
            return [
                    id: vectorFile.id,
                    status: file.status,
                    filename: file.filename,
                    created_at: file.created_at,
                    purpose: file.purpose,
                    bytes: file.bytes
            ]
        }}
    }

    @PostMapping('/uploadAndStore')
    def uploadAndStore(@RequestParam("files") MultipartFile[] multipartFiles) {
        List<File> filesToUpload = []

        // Create vector store & attach files, then wait for ingestion
        UserChatSettings userChatSettings = loadUserChatSetting()
        String vectorStoreId
        if (userChatSettings?.vectorStoreId) {
            vectorStoreId = userChatSettings?.vectorStoreId
            log.info("Vector store already exist [{}]", userChatSettings.vectorStoreId)
        } else {
            filesToUpload.add(skillTreeConceptsResourceFile.getFile())
            vectorStoreId = openAIAssistantService.createVectorStore("Synergy SkillTree Curriculum Development Store")
            log.info("Vector store created [{}]", vectorStoreId)
        }

        for (MultipartFile multipartFile : multipartFiles) {
            log.info("received file [{}]", multipartFile.originalFilename)
            def file = new File(System.getProperty('java.io.tmpdir'), multipartFile.originalFilename)
            multipartFile.transferTo(file)
            filesToUpload.add(file);
        }

        // upload all files and collect the fileIds
        log.info("Uploading [{}] file(s)...", filesToUpload.size())
        List files = filesToUpload.collect { openAIAssistantService.uploadFile(it) }
        log.info("Uploaded files: [{}]", files)

        openAIAssistantService.attachFilesToVectorStore(vectorStoreId, files.collect { it.id})
        openAIAssistantService.waitUntilVectorStoreReady(vectorStoreId)
        log.info("Vector store is ready.")
        saveUserSetting(vectorStoreId, null)
        return [vectorStoreId: vectorStoreId, files: files ]
    }

    @PostMapping("/chat")
    ChatResponse chat(@RequestBody ChatRequest chatRequest) {
        UserChatSettings userChatSettings = loadUserChatSetting()
        String conversationId = userChatSettings.conversationId
        String initialSystemInstructions = null
        if (!userChatSettings.conversationId) {
            // 1) Create a server-side conversation (OpenAI will track the context)
            initialSystemInstructions = systemInstructions
            conversationId = openAIAssistantService.createConversation()
            saveUserSetting(userChatSettings.vectorStoreId, conversationId)
            log.info("created new conversation: [{}]", conversationId)
        }

        def a1 = openAIAssistantService.askWithServerContext(conversationId, userChatSettings.vectorStoreId, initialSystemInstructions, chatRequest.question)
        log.debug("\nAnswer:\n[{}]", a1)
        return new ChatResponse(response: a1.text, conversationId: conversationId, vectorStoreId: userChatSettings.vectorStoreId)
    }

    @PostMapping("/stream/chat")
    Flux<String> streamChat(@RequestBody GenDescRequest chatRequest) {
        UserChatSettings userChatSettings = loadUserChatSetting()
        String conversationId = userChatSettings.conversationId
        String initialSystemInstructions = null
        if (!userChatSettings.conversationId) {
            // 1) Create a server-side conversation (OpenAI will track the context)
            initialSystemInstructions = systemInstructions
            conversationId = openAIAssistantService.createConversation()
            saveUserSetting(userChatSettings.vectorStoreId, conversationId)
            log.info("created new conversation: [{}]", conversationId)
        }
        return openAIAssistantService.streamAskWithServerContext(conversationId, userChatSettings.vectorStoreId, initialSystemInstructions, chatRequest.instructions).map { it.text }
    }

    @GetMapping("/userChatSettings")
    def getUserChatSettings() {
        return loadUserChatSetting()
    }

    @DeleteMapping("/userChatSettings")
    def deleteUserChatSettings() {
        return deleteUserChatSetting()
    }

    void saveUserSetting(String vectorStoreId, String conversationId) {
        String userId = userInfoService.getCurrentUserId()
        User user = userRepo.findByUserId(userId.toLowerCase())
        UserChatSettings userChatSettings = new UserChatSettings(vectorStoreId: vectorStoreId, conversationId: conversationId)
        String json = JsonOutput.toJson(userChatSettings)
        settingsService.saveSetting(new UserSettingsRequest(setting: SETTING, settingGroup: SETTING_GROUP, value: json), user, false)
    }

    UserChatSettings loadUserChatSetting() {
        String userId = userInfoService.getCurrentUserId()
        SettingsResult genProjSettings = settingsService.getUserSetting(userId, SETTING, SETTING_GROUP, false)
        return genProjSettings ? jsonSlurper.parseText(genProjSettings.value) as UserChatSettings : null
    }

    void deleteUserChatSetting() {
        String userId = userInfoService.getCurrentUserId()
        User user = userRepo.findByUserId(userId.toLowerCase())
        settingsService.deleteUserProjectSetting(SETTING, user.id)
    }

    @Canonical
    @ToString
    static class ChatResponse {
        String response
        String conversationId
        String vectorStoreId
    }

    @Canonical
    @ToString
    static class ChatRequest {
        String vectorStoreId
        String conversationId
        String question
    }

    @Canonical
    @ToString
    static class UserChatSettings {
        String vectorStoreId
        String conversationId
    }
}

