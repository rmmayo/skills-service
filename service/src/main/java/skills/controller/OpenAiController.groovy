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
import org.springframework.core.io.FileSystemResource
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
You are a chatbot designed to assist in creating a comprehensive training profile for the SkillTree application. Your primary goal is to develop a structured curriculum that can be seamlessly translated into SkillTree subjects, skills, and badges.  This is a user-friendly preview of the training that you will generate that will be show to the user for review as markdown using the following instructions.  

### Detailed Instructions:
- **Knowledge Base Utilization**:
  - Use the attached knowledge base via `file_search` to gather relevant information.
  - Reference `SkillTreeConcepts.pdf` to understand the SkillTree Platform, but do not include its content in the training material.
  - Use all other documents in the knowledge base to create a coherent and comprehensive training profile.

### Response Guidelines:
- **Structure & Format**:
  - Ensure responses are well-structured and scannable.
  - When generating detailed descriptions use Markdown for formatting, including headers, tables, and code blocks where appropriate.
  - When generating detailed descriptions use include images from the knowledge base when they enhance understanding. Images should be "data:image/png;base64" encoded and included in the directly in the markdown response.
  - **Important**: The skill description **is** the _actual lesson content_ and should teach the user everything they need to know to understand the skill and should *not* describe what the user _will learn_.  After reading the contents of the skill description the user should fully understand the skill and be able to apply it.

- **Content Quality**:
  - Provide detailed descriptions for each subject, skill, and badge.
  - Ensure that skill descriptions include clear instructions, tasks, or readings for trainees.
  - Maintain a professional and educational tone throughout.

- **Output**:
  - Initially, present the training profile in a Markdown format for easy review.
  - Be prepared to translate the training profile into a SkillTree-specific JSON format upon request.
  - Do not mention JSON format to the end user.
  - Only out the Training Profile contents as the Example Output shows below. *Do not* summarize or mention notes or gaps or any other follow up information or follow up questions to the end user.

### Handling Ambiguity:
- If the knowledge base lacks sufficient information, clearly state the gaps and suggest potential solutions.
- If uncertain about any aspect, ask clarifying questions to ensure accuracy and relevance.

### Example Output:
```markdown
# Training Profile: [Profile Name]

## Subject: [Subject Name]
- **Description**: [Brief description of the subject]
- **Skills**:
  - **[Skill Name]**: [Detailed description of the skill, including tasks or readings - use Markdown for formatting, including headers, lists, tables, and code blocks where appropriate]
  - **[Skill Name]**: [Detailed description of the skill, including tasks or readings - use Markdown for formatting, including headers, lists, tables, and code blocks where appropriate]

## Badges
- **[Badge Name]**: 
  - **Description**: [Brief description of the badge]
  - **Criteria**: [List of skills required to earn the badge]
"""
    final static String generateJsonInstructions = """
Translate the training profile into a SkillTree-specific JSON format.

### Detailed Instructions:

- **Output**:
  - The JSON should follow the format provided in the Example Output section.
  - Be sure to use Markdown formatted text for the all description values.
  - projectId, subjectId, skillId, badgeId: must be unique identifiers; be english characters only; no numbers of special characters
  - icon: icon css class from FontAwesomeFree library
  - Do not mention JSON format to the end user.
  - Do not mention Notes or gaps or any other follow up information to the end user.

### Example Output:
{
  "project": {
    "id": "projId",
    "name": "name goes here",
    "description": "detailed description"
  },
  "subjects": [
    {
      "id": "subjectId",
      "name": "Subject Name",
      "description": "subject description",
     "icon": "fa-solid fa-building",
      "skills": [
        {"name": "skill name", "skillId": "skillId", "description": "skill description", "icon": "fa-building", "pointIncrement": 10, "numOccurrencesToCompletion": 1, "selfReporting": "HonorSystem"},
        {"name": "skill name", "skillId": "skillId", "description": "skill description", "icon": "fa-building", "pointIncrement": 10, "numOccurrencesToCompletion": 1, "selfReporting": "HonorSystem"},
      ]
    },
     {
      "id": "subjectId",
      "name": "Subject Name",
      "description": "subject description",
      "icon": "fa-solid fa-building",
      "skills": [
        {"name": "skill name", "skillId": "skillId", "description": "skill description", "icon": "fa-building", "pointIncrement": 10, "numOccurrencesToCompletion": 1, "selfReporting": "HonorSystem"},
        {"name": "skill name", "skillId": "skillId", "description": "skill description", "icon": "fa-building", "pointIncrement": 10, "numOccurrencesToCompletion": 1, "selfReporting": "HonorSystem"},
      ]
    },
  ],
  "badges": [
    {
      "id": "badgeId",
      "name": "Badge Name",
      "description": "badge description",
      "icon": "fa-solid fa-building",
      "skillIds": [ "skillId1", "skillId2"]
    },
 }
"""

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
    @GetMapping("/conversations/{id}")
    def getConversation(@PathVariable("id") String conversationId) {
        return openAIAssistantService.getConversation(conversationId)
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

        def foundFiles = vectorFiles.data.collect { vectorFile -> {
            def file = allFilesParsed.data.find { it.id == vectorFile.id }
            if (file) {
                return [
                        id        : vectorFile.id,
                        status    : vectorFile.status,
                        filename  : file.filename,
                        created_at: file.created_at,
                        purpose   : file.purpose,
                        bytes     : file.bytes
                ]
            } else {
                return null
            }
        }}
        return foundFiles.collect { it }
    }

    @PostMapping('/uploadAndStore')
    def uploadAndStore(@RequestParam("files") MultipartFile[] multipartFiles) {
        List<FileSystemResource> filesToUpload = []

        // Create vector store & attach files, then wait for ingestion
        UserChatSettings userChatSettings = loadUserChatSetting()
        String vectorStoreId
        if (userChatSettings?.vectorStoreId) {
            vectorStoreId = userChatSettings?.vectorStoreId
            log.info("Vector store already exists [{}]", userChatSettings.vectorStoreId)
        } else {
            vectorStoreId = openAIAssistantService.createVectorStore("Synergy SkillTree Curriculum Development Store")
            log.info("Vector store created [{}]", vectorStoreId)
        }

        for (MultipartFile multipartFile : multipartFiles) {
            log.info("received file [{}]", multipartFile.originalFilename)
            def file = new File(System.getProperty('java.io.tmpdir'), multipartFile.originalFilename)
            multipartFile.transferTo(file)
            filesToUpload.add(new FileSystemResource(file));
        }

        // upload all files and collect the fileIds
        log.info("Uploading [{}] file(s)...", filesToUpload.size())
        List files = filesToUpload.collect { openAIAssistantService.uploadFile(it) }
        log.info("Uploaded files: [{}]", files)

        openAIAssistantService.attachFilesToVectorStore(vectorStoreId, files.collect { it.id as String})
        openAIAssistantService.waitUntilVectorStoreReady(vectorStoreId)
        log.info("Vector store is ready.")
        saveUserSetting(vectorStoreId, null)
        return [vectorStoreId: vectorStoreId, files: files ]
    }

    @DeleteMapping("/files/{fileId}")
    def deleteFile(@PathVariable("fileId") String fileId) {
        UserChatSettings userChatSettings = loadUserChatSetting()
        String vectorStoreId = userChatSettings.vectorStoreId
        assert vectorStoreId, "No vector store id found in user settings"
        return openAIAssistantService.deleteVectorStoreFile(vectorStoreId, fileId)
    }

    @PostMapping("/generateProjectJson")
    ChatResponse generateProjectJson() {
        UserChatSettings userChatSettings = loadUserChatSetting()
        String conversationId = userChatSettings.conversationId
        assert conversationId, "No conversation id found in user settings"

        def a1 = openAIAssistantService.askWithServerContext(conversationId, userChatSettings.vectorStoreId, null, generateJsonInstructions)
        log.debug("\nAnswer:\n[{}]", a1)
        return new ChatResponse(response: a1.text, conversationId: conversationId, vectorStoreId: userChatSettings.vectorStoreId)
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
        def userChatSettings = getUserChatSettings()
        openAIAssistantService.deleteVectorStore(userChatSettings.vectorStoreId)
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

