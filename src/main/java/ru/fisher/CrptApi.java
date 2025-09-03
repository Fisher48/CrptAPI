package ru.fisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.*;

/**
 * Класс для работы с API Честного Знака.
 * Потокобезопасный. Ограничивает количество запросов за указанный интервал времени.
 */
public class CrptApi {

    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;

    /**
     * @param timeUnit     Единица времени (секунда, минута и т.п.)
     * @param requestLimit Максимальное количество запросов за указанный интервал
     */
    public CrptApi(TimeUnit timeUnit, int requestLimit, long period) {
        if (requestLimit <= 0) throw new IllegalArgumentException("requestLimit must be > 0");

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.semaphore = new Semaphore(requestLimit, true);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        long periodMillis = timeUnit.toMillis(period);

        // Периодически сбрасываем лимит
        scheduler.scheduleAtFixedRate(() -> {
            semaphore.drainPermits();
            semaphore.release(requestLimit);
        }, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Создание документа для ввода в оборот.
     *
     * @param document     Объект документа
     * @param signature    Электронная подпись
     * @param productGroup Код товарной группы
     * @param token        Токен авторизации
     * @return Ответ сервера в виде строки
     */
    @SneakyThrows
    public String createDocument(Object document, String signature, String productGroup, String token) {
        semaphore.acquire(); // блокируем поток, если лимит исчерпан

        // сериализация документа
        String jsonDoc = objectMapper.writeValueAsString(document);
        String base64Doc = Base64.getEncoder().encodeToString(jsonDoc.getBytes());

        ApiRequest apiRequest = new ApiRequest
                ("MANUAL", base64Doc, productGroup, signature, "LP_INTRODUCE_GOODS");
        String body = objectMapper.writeValueAsString(apiRequest);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "?pg=" + productGroup))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        } else {
            throw new RuntimeException("API error: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     *  Метод создания документа для тестов
     *  Ответ всегда - FAKE_RESPONSE_OK
     */
    @SneakyThrows
    public String testCreateDocument(Object document, String signature,
                                     String productGroup, String token) throws InterruptedException {
        semaphore.acquire(); // блокируем поток, если лимит исчерпан

        // сериализация документа
        String jsonDoc = objectMapper.writeValueAsString(document);
        String base64Doc = Base64.getEncoder().encodeToString(jsonDoc.getBytes());

        ApiRequest apiRequest = new ApiRequest
                ("MANUAL", base64Doc, productGroup, signature, "LP_INTRODUCE_GOODS");
        String body = objectMapper.writeValueAsString(apiRequest);

        // Эмуляция сетевого запроса (2 секунды)
        Thread.sleep(2000);
        return "FAKE_RESPONSE_OK for productGroup=" + productGroup;
    }


    /**
     * Останавливает планировщик (например, при завершении приложения)
     */
    public void shutdown() {
        scheduler.shutdownNow();
    }

    // Вспомогательный класс для отправки запроса
    public record ApiRequest(String document_format, String product_document,
                           String product_group, String signature, String type) {
    }
}

