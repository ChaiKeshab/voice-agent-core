package com.example.voiceagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletion.Choice;

import okhttp3.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class VoiceService {

    /**
     * Currently the official SDK exists only for chat completion.
     */
    private static final String TRANSCRIPTION_MODEL = "whisper-1";
    private static final ChatModel CHAT_MODEL = ChatModel.GPT_4O_MINI;
    private static final String TTS_MODEL = "gpt-4o-mini-tts";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OpenAIClient openAiClient;
    private final OkHttpClient okHttp;
    private final Logger logger = LoggerFactory.getLogger(VoiceService.class);
    private final String openAiApiKey;

    public byte[] mockResponse() {
        try (InputStream is = getClass().getResourceAsStream("/sample-response.mp3")) {
            if (is == null) {
                logger.warn("Sample response audio not found, returning empty byte array");
                return new byte[0];
            }
            return is.readAllBytes();
        } catch (IOException e) {
            logger.warn("Error reading sample response audio, returning empty byte array", e);
            return new byte[0];
        }
    }

    public VoiceService(@Value("${openai.api.key}") String openAiApiKey) {
        this.openAiApiKey = openAiApiKey;
        logger.info("Initializing VoiceService");

        if (openAiApiKey == null || openAiApiKey.isEmpty()) {
            throw new IllegalStateException("OpenAI API key is required");
        }

        // OpenAI SDK client (for chat completions)
        this.openAiClient = OpenAIOkHttpClient.builder()
                .apiKey(openAiApiKey)
                .build();

        // OkHttp client (for TTS + transcription)
        this.okHttp = new OkHttpClient.Builder()
                .callTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public byte[] processWithOpenAI(byte[] audioBytes) {
        try {
            Path tempFile = Files.createTempFile("upload-", ".wav");
            Files.write(tempFile, audioBytes);

            String transcript = transcribeAudio(tempFile);
            logger.info("Transcription completed: {}", transcript);

            try {
                Files.deleteIfExists(tempFile);
            } catch (Exception ignored) {
            }

            String assistantText = generateAssistantReply(transcript);
            logger.info("Assistant reply generated: {}", assistantText);

            return synthesizeSpeech(assistantText);
        } catch (Exception e) {
            logger.error("Failed to process audio with OpenAI", e);
            throw new RuntimeException("Failed to process audio with OpenAI", e);
        }
    }

    private String transcribeAudio(Path audioPath) throws IOException {
        RequestBody fileBody = RequestBody.create(audioPath.toFile(), MediaType.parse("audio/wav"));
        MultipartBody multipart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioPath.getFileName().toString(), fileBody)
                .addFormDataPart("model", TRANSCRIPTION_MODEL)
                .build();

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/audio/transcriptions")
                .header("Authorization", "Bearer " + openAiApiKey)
                .post(multipart)
                .build();

        try (Response response = okHttp.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "no body";
                throw new IOException("Transcription request failed: " + response.code() + " - " + errorBody);
            }

            ResponseBody respBody = response.body();
            if (respBody == null) {
                throw new IOException("Empty transcription response");
            }

            String responseString = respBody.string();
            JsonNode root = MAPPER.readTree(responseString);
            JsonNode textNode = root.path("text");
            return textNode.isTextual() ? textNode.asText() : "";
        }
    }

    private String generateAssistantReply(String transcript) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addSystemMessage(
                        "You are a friendly, concise customer support agent for ExampleCorp. " +
                                "Read the user's transcribed message and respond with: summary of issue, " +
                                "one-line next step, and a polite closing. Keep it short (<= 120 words).")
                .addUserMessage(transcript)
                .model(CHAT_MODEL)
                .build();

        ChatCompletion chatCompletion = openAiClient.chat().completions().create(params);

        List<Choice> choices = chatCompletion.choices();
        if (!choices.isEmpty()) {
            ChatCompletionMessage message = choices.get(0).message();
            if (message != null) {
                return message.content()
                        .map(String::trim)
                        .orElse("Sorry, I couldn't generate a response at this time.");
            }
        }
        return "Sorry, I couldn't generate a response at this time.";
    }

    private byte[] synthesizeSpeech(String text) throws IOException {
        ObjectNode bodyJson = MAPPER.createObjectNode();
        bodyJson.put("model", TTS_MODEL);
        bodyJson.put("input", text);
        bodyJson.put("voice", "alloy");
        bodyJson.put("response_format", "mp3");

        RequestBody requestBody = RequestBody.create(
                MAPPER.writeValueAsBytes(bodyJson),
                MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/audio/speech")
                .header("Authorization", "Bearer " + openAiApiKey)
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build();

        try (Response response = okHttp.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "no body";
                throw new IOException("TTS request failed: " + response.code() + " - " + errorBody);
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("Empty TTS response");
            }

            return responseBody.bytes();
        }
    }
}
