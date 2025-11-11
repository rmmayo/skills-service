package skills.auth.openai

import groovy.util.logging.Slf4j
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.core.io.FileSystemResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.BodyInserters
import groovy.json.JsonSlurper

@Service
@Slf4j
class OpenAIAssistantService2 {

    private final JsonSlurper jsonSlurper = new JsonSlurper()

    private String vectorStoreId
    private String assistantId
    private String threadId

    private static final String BASE_URL = "https://api.openai.com/v1"

    @Value('#{"${skills.openai.host:null}"}')
    String openAiHost

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

    // --- Helper Methods (Unchanged) ---

    private Map pollStatus(String path, List<String> completeStatuses, List<String> failStatuses) {
        Map result = webClient.get().uri(path).retrieve().bodyToMono(Map).block()

        while (!completeStatuses.contains(result?.status) && !failStatuses.contains(result?.status)) {
            println "Polling status: ${result?.status}..."
            Thread.sleep(2000) // Wait 2 seconds
            result = webClient.get().uri(path).retrieve().bodyToMono(Map).block()
        }

        if (failStatuses.contains(result?.status)) {
            throw new RuntimeException("Operation failed. Status: ${result?.status}, Error: ${result?.last_error?.message}")
        }
        return result
    }

    private Map createVectorStore() {
        println "Creating new Vector Store..."
        def requestBody = [
                name: "Project Knowledge Base",
        ]

        return webClient.post().uri("/vector_stores")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map)
                .block()
    }

    private Map createAssistant() {
        println "Creating assistant with File Search enabled..."
        def requestBody = [
                model: "gpt-4-turbo",
                name: "Document Reader V2 (WebClient)",
                instructions: "You are a helpful assistant that answers questions based only on the provided documents found via file search.",
                tools: [[type: "file_search"]],
                tool_resources: [
                        file_search: [
                                vector_store_ids: [vectorStoreId]
                        ]
                ]
        ]

        return webClient.post().uri("/assistants")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map)
                .block()
    }

    // --- UPDATED METHOD ---
    /**
     * Step 2: Uploads multiple files to the Vector Store using a file batch API.
     * @param filePaths List of local paths to files.
     */
//    private void uploadFilesToVectorStore(List<File> files) {
//        if (!files) {
//            println "No files provided for upload."
//            return
//        }
//
//        println "Uploading ${files.size()} files to Vector Store..."
//
//        // Use MultipartBodyBuilder to construct the multi-file upload request
//        MultipartBodyBuilder builder = new MultipartBodyBuilder()
//        builder.part("purpose", "vector_store") // Purpose is required for the file endpoint
//
//        // Add each File as a part of the multipart body
//        files.each { File file ->
//            def resource = new FileSystemResource(file)
//            // The file part is named "file" for the OpenAI API
//            builder.part("file", resource)
//        }
//
//        // Use the /files endpoint for multi-file upload
//        def filesResponse = webClient.post().uri("/files")
//                .contentType(MediaType.MULTIPART_FORM_DATA)
//                .body(BodyInserters.fromMultipartData(builder.build()))
//                .retrieve()
//                .bodyToFlux(Map)
//                .collectList()
//                .block()
//
//        // Extract file IDs from the responses
//        def fileIds = filesResponse.collect { it?.id }.findAll { it != null }
//        println "Uploaded files. IDs: ${fileIds}"
//
//        // Now, create a file batch to link these files to the Vector Store
//        def batchBody = [
//                file_ids: fileIds
//        ]
//
//        // Using the file_batches endpoint to link the files to the vector store
//        def batchResponse = webClient.post().uri("/vector_stores/${vectorStoreId}/file_batches")
//                .bodyValue(batchBody)
//                .retrieve()
//                .bodyToMono(Map)
//                .block()
//
//        def batchId = batchResponse?.id
//        println "File batch created. Batch ID: ${batchId}"
//
//        // Poll the file batch status until complete (Vector Store indexing)
//        def batchPath = "/vector_stores/${vectorStoreId}/file_batches/${batchId}"
//        pollStatus(batchPath, ["completed"], ["failed", "cancelled", "expired"])
//
//        println "Vector Store indexing complete for ${files.size()} files."
//    }
    private void uploadFilesToVectorStore(List<File> files) {
        if (!files) {
            println "No files provided for upload."
            return
        }

        println "Uploading ${files.size()} files to Vector Store..."

        MultipartBodyBuilder builder = new MultipartBodyBuilder()

        files.each { File file ->
            def resource = new FileSystemResource(file)
            // The file part MUST be named "file"
            builder.part("file", resource).filename(file.getName())
        }

        // 1. Upload files directly to the dedicated vector store endpoint
        def batchResponse = webClient.post().uri("/vector_stores/${vectorStoreId}/files")
        // This header MUST be set for file upload
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(Map)
                .block()

        def batchId = batchResponse?.id
        println "File batch created and uploaded. Batch ID: ${batchId}"

        // 2. Poll the file batch status until complete (Vector Store indexing)
        def batchPath = "/vector_stores/${vectorStoreId}/file_batches/${batchId}"
        pollStatus(batchPath, ["completed"], ["failed", "cancelled", "expired"])

        println "Vector Store indexing complete for ${files.size()} files."
    }

    // --- Public Workflow Methods (Updated Signature) ---

    OpenAIAssistant setupAssistant(List<File> files) { // Accepts a List<String>
        def vsResponse = createVectorStore()
        vectorStoreId = vsResponse?.id

        uploadFilesToVectorStore(files) // Call the new multi-file method

        def assistantResponse = createAssistant()
        assistantId = assistantResponse?.id

        // Create a new Thread
        def threadResponse = webClient.post().uri("/threads").retrieve().bodyToMono(Map).block()
        threadId = threadResponse?.id

        println "Assistant setup complete. VS ID: ${vectorStoreId}, Assistant ID: ${assistantId}, Thread ID: ${threadId}"
        return new OpenAIAssistant(vectorStoreId: vectorStoreId, assistantId: assistantId, threadId: threadId)
    }

    // The askFollowUp method remains UNCHANGED as the vector store handles the retrieval internally.

    String askFollowUp(String question, String threadId, String assistantId) {
        if (!threadId || !assistantId) {
            return "Assistant is not set up. Please call setupAssistant first."
        }

        // 1. Add the user's message (question) to the Thread
        def messageBody = [
                role: "user",
                content: question
        ]
        webClient.post().uri("/threads/${threadId}/messages")
                .bodyValue(messageBody)
                .retrieve()
                .bodyToMono(Map)
                .block()
        println "User message added to thread."

        // 2. Create and Run the Assistant on the Thread
        def runBody = [
                assistant_id: assistantId
        ]
        def runResponse = webClient.post().uri("/threads/${threadId}/runs")
                .bodyValue(runBody)
                .retrieve()
                .bodyToMono(Map)
                .block()
        def runId = runResponse?.id

        // 3. Poll for the Run status until it completes
        def runPath = "/threads/${threadId}/runs/${runId}"
        def finalRun = pollStatus(runPath, ["completed"], ["failed", "cancelled", "expired"])

        // 4. Retrieve and return the final response message
        def messagesResponse = webClient.get().uri("/threads/${threadId}/messages?order=desc&limit=1")
                .retrieve()
                .bodyToMono(Map)
                .block()

        def assistantMessage = messagesResponse?.data?.find { it?.role == "assistant" }

        // Extract the text content
        def responseText = assistantMessage?.content?.find { it?.type == "text" }
                ?.text?.value ?: "No text content found in response."

        return responseText
    }

    static class OpenAIAssistant {
        String vectorStoreId
        String assistantId
        String threadId
    }
}