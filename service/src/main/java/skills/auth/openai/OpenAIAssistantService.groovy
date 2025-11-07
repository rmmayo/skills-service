package skills.auth.openai

import groovy.util.logging.Slf4j
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

import java.time.Duration

@Service
@Slf4j
class OpenAIAssistantService {

    @Value('#{"${skills.openai.host:null}"}')
    String openAiHost

    @Value('#{"${skills.openai.completionsEndpoint:/v1/chat/completions}"}')
    String completionsEndpoint

    @Value('#{"${skills.openai.modelsEndpoint:/v1/models}"}')
    String modelsEndpoint

    @Value('#{"${skills.openai.filesEndpoint:/v1/files}"}')
    String filesEndpoint

    @Value('#{"${skills.openai.assistantsEndpoint:/v1/assistants}"}')
    String assistantsEndpoint

    @Value('#{"${skills.openai.threadsEndpoint:/v1/beta/threads}"}')
    String threadsEndpoint

    @Value('#{"${skills.openai.key:null}"}')
    String openAiKey

    @Autowired
    SslWebClientConfig sslWebClientConfig

    WebClient webClient

    @PostConstruct
    void initWebClient() {
        if (openAiHost) {
            this.webClient = sslWebClientConfig.createWebClient("${openAiHost}/v1")
        } else {
            log.debug("skills.openai.host is not configured")
        }
    }

    def listFiles() {
        return callGetEndpoint("/files")
    }

    def getThread(String threadId) {
        return callGetEndpoint("/threads/${threadId}".toString())
    }

    def listVectorStores() {
        return callGetEndpoint("/vector_stores")
    }

    def getVectorStore(String storeId) {
        return callGetEndpoint("/vector_stores/${storeId}".toString())
    }

    def getVectorStoreFiles(String storeId) {
        return callGetEndpoint("/vector_stores/${storeId}/files".toString())
    }


    private def callGetEndpoint(String endpoint) {
        return webClient.get()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(Map)
                .block()
    }


    /** STEP 1: Create a vector store and upload the files */
    String uploadFilesToVectorStore(List<File> files) {
        List<String> fileIds = []
        for (File file : files) {
            // 1️⃣ Upload the file normally
            def resource = new FileSystemResource(file)
            def multipart = BodyInserters
                    .fromMultipartData("file", resource)
                    .with("purpose", "assistants")

            def uploadedFile = webClient.post()
                    .uri("/files")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart)
                    .retrieve()
                    .bodyToMono(Map)
                    .block()

            String fileId = uploadedFile.id
            fileIds.add(fileId)
        }

        // 2️⃣ Create a new vector store
        def vectorStore = webClient.post()
                .uri("/vector_stores")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue([name: "assistant-docs"]))
                .retrieve()
                .bodyToMono(Map)
                .block()

        def vectorStoreId = vectorStore.id

        // 3️⃣ Attach the uploaded files to that store via JSON
        for (String fileId : fileIds) {
            webClient.post()
                    .uri("/vector_stores/${vectorStoreId}/files")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue([file_id: fileId]))
                    .retrieve()
                    .bodyToMono(Map)
                    .block()

            log.info("File uploaded to vector store [{}]", vectorStoreId)
        }
        return vectorStoreId
    }

    /** STEP 2: Create the Assistant linked to that vector store */
    Mono<String> createAssistant(String name, String instructions, String model, String vectorStoreId) {
        def body = [
                name          : name,
                instructions  : instructions,
                model         : model,
                tools         : [[type: "file_search"]],
                tool_resources: [
                        file_search: [
                                vector_store_ids: [vectorStoreId]
                        ]
                ]
        ]

        log.info("Creating Assistant linked to vector store [{}]", vectorStoreId)
        return webClient.post()
                .uri('/assistants')
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(Map)
                .map { it.id as String }
    }

    /** STEP 3: Create a new conversation thread */
    Mono<String> createThread() {

        log.info("Creating new conversation thread")
        return webClient.post()
                .uri('/threads')
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue([:]))
                .retrieve()
                .bodyToMono(Map)
                .map { it.id as String }
    }

    /** STEP 4: Add a user message to the thread */
    Mono<String> addUserMessage(String threadId, String content) {
        def body = [role: 'user', content: content]
        log.info("Adding user message [{}] to conversation thread [{}]", content, threadId)
        return webClient.post()
                .uri("/threads/${threadId}/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(Map)
                .map { it.id as String }
    }

    /** STEP 5: Run the Assistant on that thread */
    Mono<String> runAssistant(String threadId, String assistantId) {
        def body = [assistant_id: assistantId]

        log.info("Running assistant [{}] on conversation thread [{}]", assistantId, threadId)
        return webClient.post()
                .uri("/threads/${threadId}/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body))
                .retrieve()
                .bodyToMono(Map)
                .map { it.id as String }
    }

    /** STEP 6: Poll the run until it completes */
    Mono<String> pollRunUntilComplete(String threadId, String runId) {
        log.info("Polling conversation thread [{}] for run [{}]", threadId, runId)
        return webClient.get()
                .uri("/threads/${threadId}/runs/${runId}")
                .retrieve()
                .bodyToMono(Map)
                .delayElement(Duration.ofSeconds(1))
                .flatMap { Map run ->
                    if (run.status == 'completed') {
                        return Mono.just('completed');
                    } else if (run.status == 'failed') {
                        String errorMessage = "Failed while polling external service.  [${run?.last_error}]"
                        log.error(errorMessage)
                        throw new RuntimeException(errorMessage)
                    }
                    else {
                        pollRunUntilComplete(threadId, runId)
                    }
                }
    }

    /** STEP 7: Retrieve the assistant’s final message */
    Mono<String> getAssistantResponse(String threadId) {
        log.info("Retrieving assistant's final message on conversation thread [{}]", threadId)
        return webClient.get()
                .uri("/threads/${threadId}/messages")
                .retrieve()
                .bodyToMono(Map)
                .map { Map m ->
                    def messages = m.data as List<Map>
                    def assistantMsg = messages.find { it.role == 'assistant' }
                    def content = assistantMsg?.content?.getAt(0)?.text?.value
                    content ?: '(no assistant response)'
                }
    }
}