package skills.auth.openai

import groovy.json.JsonSlurper
import groovy.transform.Canonical
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

import java.time.Duration

@Service
@Slf4j
class OpenAIAssistantService {


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

    String uploadFile(File file) {
        return uploadFile(new FileSystemResource(file))
    }

    /**
     * Upload a single file using the Files API with purpose 'user_data'.
     * Docs: File inputs & Files API.
     */
    String uploadFile(Resource resource) {
        def mb = new MultipartBodyBuilder()
        mb.part("purpose", "user_data")
        mb.part("file", resource).filename(resource.filename)

        Map resp = webClient.post()
                .uri("/files")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(mb.build()))
                .retrieve()
                .bodyToMono(Map)
                .block()

        return (String) resp.get("id")
    }

    /**
     * Create a vector store to power file_search.
     * Docs: Vector stores reference.
     */
    String createVectorStore(String name) {
        Map body = [name: name]
        Map resp = webClient.post()
                .uri("/vector_stores")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map)
                .block()
        return (String) resp.get("id")
    }

    /**
     * Attach multiple file IDs to the vector store in a single batch.
     * Docs: vector-stores file batches.
     */
    void attachFilesToVectorStore(String vectorStoreId, List<String> fileIds) {
        Map body = [file_ids: fileIds]
        webClient.post()
                .uri("/vector_stores/${vectorStoreId}/file_batches")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map)
                .block()
    }

    /**
     * Poll until all files in the vector store are processed (not in_progress).
     * Docs suggest polling until ingestion is complete before querying.
     */
    void waitUntilVectorStoreReady(String vectorStoreId) {
        while (true) {
            Map listing = webClient.get()
                    .uri { b -> b.path("/vector_stores/${vectorStoreId}/files").queryParam("limit", "100").build() }
                    .retrieve()
                    .bodyToMono(Map)
                    .block()

            List<Map> files = (List<Map>) listing.get("data")
            if (files != null && files.every { f -> !"in_progress".equalsIgnoreCase((String) f.get("status")) }) {
                return
            }
            Thread.sleep(1500L)
        }
    }

    /**
     * Ask a question using the Responses API with the file_search tool.
     * The vector store is supplied via tool_resources.file_search.vector_store_ids.
     * Docs: Responses API + file_search guide.
     */
    String askWithFileSearch(String vectorStoreId, List<Map> messages) {
        Map body = [
                model: "gpt-5",
                input: messages,
                tools: [[type: "file_search", vector_store_ids: [vectorStoreId]]]
                // (optional) response_format / temperature / max_output_tokens, etc…
        ]

        Map resp = webClient.post()
                .uri("/responses")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map)
                .block()

        return extractText(resp)
    }

    /** Create a persistent conversation to let OpenAI manage multi-turn context. */
    String createConversation() {
        Map resp = webClient.post()
                .uri("/conversations")
                .bodyValue([:]) // empty payload is fine
                .retrieve()
                .bodyToMono(Map)
                .block()
        return (String) resp.get("id")
    }

    /**
     * Ask a question with OpenAI-managed context.
     * - Keep using the same conversationId (server-side state).
     * - Configure file_search by putting vector_store_ids on the tool item.
     */
    Response askWithServerContext(String conversationId, String vectorStoreId, String maybeSystemInstruction, String userQuestion) {
        def inputItems = []
        if (maybeSystemInstruction) {
            inputItems << [
                    role   : "system",
                    content: [[type: "input_text", text: maybeSystemInstruction]]
            ]
        }
        inputItems << [
                role   : "user",
                content: [[type: "input_text", text: userQuestion]]
        ]

        Map body = [
                model       : "gpt-5",
                store       : true,                // persist context on the server
                conversation: conversationId,      // keep using the same conversation
                input       : inputItems,
                tools       : [[
                                       type            : "file_search",
                                       vector_store_ids: [vectorStoreId]
                               ]]
        ]

        Map resp = webClient.post()
                .uri("/responses")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map)
                .block()

        return new Response(
                id: (String) resp.get("id"),
                text: extractText(resp)
        )
    }

    /**
     * Tolerant extractor for Responses API payloads:
     * 1) Prefer top-level 'output_text' if present (many SDKs expose this).
     * 2) Otherwise, concatenate any output_text or content[].text segments.
     */
    private static String extractText(Map resp) {
        if (resp == null) return ""
        def outputText = resp.get("output_text")
        if (outputText instanceof String && !outputText.isBlank()) {
            return (String) outputText
        }
        // Fall back to walking output[] -> content[] -> text
        StringBuilder sb = new StringBuilder()
        def outputs = resp.get("output")
        if (outputs instanceof List) {
            outputs.each { o ->
                def content = (o instanceof Map) ? o.get("content") : null
                if (content instanceof List) {
                    content.each { c ->
                        if (c instanceof Map) {
                            def type = c.get("type")
                            def text = c.get("text")
                            if (("output_text" == type || "input_text" == type) && text instanceof String) {
                                sb.append(text).append("\n")
                            }
                        }
                    }
                }
            }
        }
        return sb.toString().trim()
    }

    @Canonical
    @ToString
    static class OpenAIAssistant {
        String vectorStoreId
        String assistantId
        String threadId
    }

    @Canonical
    @ToString
    static class Response {
        String id
        String text
    }
}