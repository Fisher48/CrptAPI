package ru.fisher;

import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        // Создаём API-клиент: не более 3 запросов в 5 секунд
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 3, 5);

        // Заглушка документа
        CrptApi.ApiRequest doc = new CrptApi.ApiRequest(
                "JSON",
                "Содержимое документа",
                "1",
                "Sign",
                "AGGREGATION_DOCUMENT"
        );

        String fakeSignature = "test-signature";
        String fakeToken = "test-token";
        String fakeProductGroup = "test-group";

        for (int i = 1; i <= 10; i++) {
            final int requestId = i;
            new Thread(() -> {
                try {
                    System.out.println("Отправка запроса #" + requestId + " в " + Thread.currentThread().getName());
                    // Вызов метода. Он будет блокировать поток, если лимит превышен.
                    String response = api.testCreateDocument(doc, fakeSignature, fakeProductGroup, fakeToken);
                    System.out.println("Ответ на запрос #" + requestId + ": " + response);
                } catch (Exception e) {
                    System.out.println("Ошибка запроса #" + requestId + ": " + e.getMessage());
                }
            }).start();
        }

        // Даём потокам поработать
        Thread.sleep(20000);
        api.shutdown();
    }
}