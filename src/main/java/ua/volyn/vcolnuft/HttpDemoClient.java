package ua.volyn.vcolnuft;

// Група Jackson (відповідає за перетворення JSON <-> Java об'єкт)
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // Дозволяє ігнорувати зайві поля в JSON, щоб програма не падала з помилкою.
import com.fasterxml.jackson.core.JsonProcessingException;   // Клас помилки, яка виникає, якщо JSON некоректний або його не можна обробити.
import com.fasterxml.jackson.databind.ObjectMapper;          // Головний інструмент Jackson. Саме він виконує "магію" перетворення тексту в об'єкти.

// Група Unirest (відповідає за HTTP-запити в інтернет)
import kong.unirest.core.HttpResponse; // "Конверт" відповіді від сервера (містить статус код, заголовки і самі дані).
import kong.unirest.core.Unirest;      // Головний клас для відправки запитів (GET, POST тощо) і налаштування клієнта.

// Стандартні бібліотеки Java (вбудовані)
import java.io.FileWriter;                 // Дозволяє записувати текст у файли (використовуємо для CSV).
import java.time.LocalDateTime;            // Робота з поточною датою та часом (без часових поясів).
import java.time.format.DateTimeFormatter; // Дозволяє красиво форматувати дату (наприклад, перетворити дату у рядок для назви файлу).
import java.util.Scanner;                  // Зчитує дані, які користувач вводить з клавіатури в консоль.

public class HttpDemoClient {

    public static void main(String[] args) {

        Unirest.config()
                .connectTimeout(5000)
                .setObjectMapper(new kong.unirest.core.ObjectMapper() {
                    final ObjectMapper mapper = new ObjectMapper();
                    public String writeValue(Object value) {
                        try {
                            return mapper.writeValueAsString(value);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    public <T> T readValue(String value, Class<T> valueType) {
                        try {
                            return mapper.readValue(value, valueType);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    }
                })
                .setDefaultHeader("Accept", "application/json");

        System.out.println("=== HTTP Client for Jikan Anime API ===");
        runMenu();
    }

    public static void runMenu() {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.println("\nJikan Anime API Menu:");
            System.out.println("1 — Топ аніме");
            System.out.println("2 — Деталі аніме за ID");
            System.out.println("3 — Зберегти топ у CSV");
            System.out.println("4 — Вийти");
            System.out.print("Ваш вибір: ");

            switch (sc.nextLine()) {
                case "1" -> getTopAnime();
                case "2" -> {
                    System.out.print("Введіть ID аніме (наприклад 1, 5114, 20): ");
                    String id = sc.nextLine();
                    getAnimeDetails(id);
                }
                case "3" -> saveTopAnimeToCsv();
                case "4" -> {
                    System.out.println("До побачення!");
                    Unirest.shutDown();
                    return;
                }
                default -> System.out.println("Невірний вибір");
            }
        }
    }

    // ============================================
    // 1. Топ аніме
    // ============================================

    public static void getTopAnime() {
        try {
            System.out.println("\n--- Отримання топ аніме ---");

            HttpResponse<JikanTopResponse> resp = Unirest
                    .get("https://api.jikan.moe/v4/top/anime")
                    .asObject(JikanTopResponse.class);

            if (!resp.isSuccess()) {
                System.out.println("Помилка HTTP: " + resp.getStatus());
                return;
            }

            JikanTopResponse top = resp.getBody();

            System.out.println("Топ аніме:");
            for (int i = 0; i < Math.min(10, top.data.length); i++) {
                AnimeShort a = top.data[i];
                System.out.println((i + 1) + ". " + a.title + " (ID: " + a.mal_id + ")");
            }

        } catch (Exception e) {
            System.err.println("Помилка: " + e.getMessage());
        }
    }

    // ============================================
    // 2. Деталі аніме по ID
    // ============================================

    public static void getAnimeDetails(String id) {
        try {
            System.out.println("\n--- Деталі аніме ID: " + id + " ---");

            HttpResponse<JikanAnimeResponse> resp = Unirest
                    .get("https://api.jikan.moe/v4/anime/" + id)
                    .asObject(JikanAnimeResponse.class);

            if (!resp.isSuccess()) {
                System.out.println("Помилка HTTP: " + resp.getStatus());
                return;
            }

            AnimeDetails anime = resp.getBody().data;

            System.out.println("Назва: " + anime.title);
            System.out.println("Рейтинг: " + anime.score);
            System.out.println("Тип: " + anime.type);
            System.out.println("Епізоди: " + anime.episodes);
            System.out.println("Опис:\n" + anime.synopsis);

        } catch (Exception e) {
            System.err.println("Помилка: " + e.getMessage());
        }
    }

    // ============================================
    // 3. Збереження топ аніме у CSV
    // ============================================

    public static void saveTopAnimeToCsv() {
        try {
            System.out.println("\n--- Збереження топ аніме у CSV ---");

            HttpResponse<JikanTopResponse> resp = Unirest
                    .get("https://api.jikan.moe/v4/top/anime")
                    .asObject(JikanTopResponse.class);

            if (!resp.isSuccess()) {
                System.out.println("Помилка завантаження даних!");
                return;
            }

            JikanTopResponse top = resp.getBody();

            String file = "top_anime_" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                    + ".csv";

            FileWriter fw = new FileWriter(file);
            fw.write("ID,Назва,Рейтинг,Тип,Епізоди\n");

            for (AnimeShort a : top.data) {
                fw.write(a.mal_id + ",");
                fw.write("\"" + a.title.replace("\"","\"\"") + "\",");
                fw.write(a.score + ",");
                fw.write(a.type + ",");
                fw.write(a.episodes + "\n");
            }

            fw.close();

            System.out.println("CSV збережено у файл: " + file);
            System.out.println("Кількість аніме: " + top.data.length);

        } catch (Exception e) {
            System.err.println("Помилка CSV: " + e.getMessage());
        }
    }

    // ============================================
    // Класи для JSON
    // ============================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class JikanTopResponse {
        public AnimeShort[] data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AnimeShort {
        public int mal_id;
        public String title;
        public String type;
        public Integer episodes;
        public Double score;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class JikanAnimeResponse {
        public AnimeDetails data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AnimeDetails {
        public int mal_id;
        public String title;
        public String synopsis;
        public Double score;
        public String type;
        public Integer episodes;
    }
}
