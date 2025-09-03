package ru.fisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.SneakyThrows;

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
    private final long period;
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
        this.period = period;
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
    public String createDocument(Document document, String signature, String productGroup, String token) {
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
    public String testCreateDocument(Document document, String signature,
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

    // DTO классы
    @Data
    public static class ApiRequest {
        private final String document_format;
        private final String product_document;
        private final String product_group;
        private final String signature;
        private final String type;
    }

    @Data
    public static class Document {
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private Boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private Product[] products;
        private String reg_date;
        private String reg_number;
    }

    @Data
    public static class Description {
        private String participantInn;
    }

    @Data
    public static class Product {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }
}

