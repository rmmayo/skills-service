package skills.auth.openai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.Canonical
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.SynchronousSink

@Service
@Slf4j
class OpenAIAssistantService {

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

    def getConversation(String conversationId) {
        return callGetEndpoint("/conversations/${conversationId}".toString())
    }

    def deleteVectorStoreFile(String vectorStoreId, String fileId) {
        callDeleteEndpoint("/files/${fileId}")
        def res = callDeleteEndpoint("/vector_stores/${vectorStoreId}/files/${fileId}")
        return res
    }

    def deleteVectorStore(String vectorStoreId) {
        def vectorStoreFiles = getVectorStoreFiles(vectorStoreId)
        vectorStoreFiles.data.each { file ->
            deleteFile(file.id)
        }
        return callDeleteEndpoint("/vector_stores/${vectorStoreId}")
    }

    def deleteFile(String fileId) {
        return callDeleteEndpoint("/files/${fileId}")
    }

    private def callGetEndpoint(String endpoint) {
        return webClient.get()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(Map)
                .block()
    }

    private def callDeleteEndpoint(String endpoint) {
        return webClient.delete()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(Map)
                .block()
    }

    /**
     * Upload a single file using the Files API with purpose 'user_data'.
     * Docs: File inputs & Files API.
     */
    Map uploadFile(Resource resource) {
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

        return resp
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
        Integer counter = 0
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
            if (counter++ % 10 == 0) {
                log.info("waitUntilVectorStoreReady: iteration ${counter}, still waiting for vector store to be ready")
            }
            Thread.sleep(1500L)
        }
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
     * Streams the model's output text using SSE.
     * Emits StreamChunk(type: "delta", text: "...") for each text token,
     * and a final StreamChunk(type: "completed", responseId: "...") when done.
     */
    Flux<StreamChunk> streamAskWithServerContext(
            String conversationId,
            String vectorStoreId,
            String maybeSystemInstruction,
            String userQuestion
    ) {
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
                model        : "gpt-5",
                store        : true,                 // Keep context on the server
                conversation : conversationId,       // Bind to the same conversation
                input        : inputItems,
                tools        : [[
                                        type            : "file_search",
                                        vector_store_ids: [vectorStoreId]
                                ]],
                stream       : true                  // 🔑 enable SSE streaming
        ]

        def typeRef = new ParameterizedTypeReference<ServerSentEvent<String>>() {}
        def mapper  = new ObjectMapper()

        return webClient.post()
            .uri("/responses")
            .headers { h ->
                h.setAccept([MediaType.TEXT_EVENT_STREAM])
            }
            .bodyValue(body)
            .retrieve()
            .bodyToFlux(typeRef)
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.error("WebClient error: Status={}, Body={}", ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
                return Flux.error(new RuntimeException("Failed to process OpenAI streaming response", ex));
            })
            .onErrorResume(Throwable.class, ex -> {
                log.error("Unexpected error during OpenAI streaming", ex);
                return Flux.error(new RuntimeException("Unexpected error during streaming", ex));
            })
            .handle { ServerSentEvent<String> sse, SynchronousSink<StreamChunk> sink ->
                String event = sse.event()
                String data  = sse.data()
                if (data == null) return
                JsonNode node = mapper.readTree(data)

                // Error handling
                if ("response.error".equals(event)) {
                    String msg = node.path("error").path("message").asText("Unknown streaming error")
                    sink.error(new RuntimeException(msg)); return
                }

                // Token-by-token text
                if ("response.output_text.delta".equals(event)) {
                    String delta = node.path("delta").asText("")
                    delta = delta.replaceAll('\\n', '<<newline>>')
                    log.trace("Response: [{}] from json=[{}]", delta, node)
                    if (!delta.isEmpty()) sink.next(new StreamChunk(type: "delta", text: delta))
                    return
                }

                // Some models emit a final consolidated text chunk
                if ("response.output_text.done".equals(event)) {
                    String text = node.path("text").asText("")
                    log.debug("Response: [{}] from json=[{}]", text, node)
                    return
                }

                // Stream finished
                if ("response.completed".equals(event)) {
                    // Response id may appear either at root or under "response"
                    String respId = node.path("response").path("id").asText(null)
                    if (!respId) respId = node.path("id").asText(null)
                    sink.next(new StreamChunk(type: "completed", responseId: respId, text: '[DONE]'))
                    sink.complete()
                }

                // Ignore other event types (tool calls, citations, etc) for brevity
            }
    }

    /** Simple DTO for streamed events */
    static class StreamChunk {
        String type    // "delta" | "completed"
        String text    // present when type=="delta"
        String responseId // present when type=="completed"
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