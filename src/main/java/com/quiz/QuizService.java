package com.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class QuizService {

    private static final String BASE_URL = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";
    private static final int TOTAL_POLLS = 10;
    private static final int DELAY_MS = 5000; // 5 seconds mandatory delay

    private final String regNo;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public QuizService(String regNo) {
        this.regNo = regNo;
        this.httpClient = HttpClient.newHttpClient();
        this.mapper = new ObjectMapper();
    }

    public void run() throws Exception {
        System.out.println("=== Quiz Leaderboard System ===");
        System.out.println("RegNo: " + regNo);
        System.out.println();

        // Step 1: Poll API 10 times and collect all events
        List<Map<String, Object>> allEvents = new ArrayList<>();

        for (int poll = 0; poll < TOTAL_POLLS; poll++) {
            System.out.println("Polling " + poll + "/" + (TOTAL_POLLS - 1) + "...");
            List<Map<String, Object>> events = fetchEvents(poll);
            allEvents.addAll(events);
            System.out.println("  → Got " + events.size() + " events");
            for (Map<String, Object> ev : events) {
                System.out
                        .println("     " + ev.get("roundId") + " | " + ev.get("participant") + " | " + ev.get("score"));
            }

            // Mandatory 5-second delay (skip after last poll)
            if (poll < TOTAL_POLLS - 1) {
                Thread.sleep(DELAY_MS);
            }
        }

        System.out.println("\nTotal raw events collected: " + allEvents.size());

        // Step 2: Deduplicate using (roundId + participant) as unique key
        Map<String, Map<String, Object>> deduplicatedMap = new LinkedHashMap<>();
        int duplicatesSkipped = 0;

        for (Map<String, Object> event : allEvents) {
            String roundId = (String) event.get("roundId");
            String participant = (String) event.get("participant");
            String key = roundId + "|" + participant;

            if (deduplicatedMap.containsKey(key)) {
                duplicatesSkipped++;
                System.out.println("  [DUPLICATE SKIPPED] roundId=" + roundId + " participant=" + participant);
            } else {
                deduplicatedMap.put(key, event);
            }
        }

        System.out.println("\nDuplicates removed: " + duplicatesSkipped);
        System.out.println("Unique events: " + deduplicatedMap.size());

        // Step 3: Aggregate scores per participant
        Map<String, Integer> scoreMap = new LinkedHashMap<>();
        for (Map<String, Object> event : deduplicatedMap.values()) {
            String participant = (String) event.get("participant");
            int score = ((Number) event.get("score")).intValue();
            scoreMap.merge(participant, score, Integer::sum);
        }

        // Step 4: Sort leaderboard by totalScore descending
        List<Map.Entry<String, Integer>> leaderboard = new ArrayList<>(scoreMap.entrySet());
        leaderboard.sort((a, b) -> b.getValue() - a.getValue());

        // Step 5: Print leaderboard
        System.out.println("\n=== LEADERBOARD ===");
        int rank = 1;
        int grandTotal = 0;
        for (Map.Entry<String, Integer> entry : leaderboard) {
            System.out.printf("  #%d %-20s %d pts%n", rank++, entry.getKey(), entry.getValue());
            grandTotal += entry.getValue();
        }
        System.out.println("----------------------------");
        System.out.println("  Grand Total Score: " + grandTotal);

        // Step 6: Submit leaderboard ONCE
        System.out.println("\nSubmitting leaderboard...");
        submitLeaderboard(leaderboard);
    }

    // --- API: GET /quiz/messages ---
    private List<Map<String, Object>> fetchEvents(int poll) throws Exception {
        String url = BASE_URL + "/quiz/messages?regNo=" + regNo + "&poll=" + poll;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Poll " + poll + " failed: HTTP " + response.statusCode() + " → " + response.body());
        }

        JsonNode root = mapper.readTree(response.body());
        JsonNode events = root.get("events");

        List<Map<String, Object>> result = new ArrayList<>();
        if (events != null && events.isArray()) {
            for (JsonNode event : events) {
                Map<String, Object> e = new HashMap<>();
                e.put("roundId", event.get("roundId").asText());
                e.put("participant", event.get("participant").asText());
                e.put("score", event.get("score").asInt());
                result.add(e);
            }
        }
        return result;
    }

    // --- API: POST /quiz/submit ---
    private void submitLeaderboard(List<Map.Entry<String, Integer>> leaderboard) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("regNo", regNo);

        ArrayNode lb = body.putArray("leaderboard");
        for (Map.Entry<String, Integer> entry : leaderboard) {
            ObjectNode item = lb.addObject();
            item.put("participant", entry.getKey());
            item.put("totalScore", entry.getValue());
        }

        String jsonBody = mapper.writeValueAsString(body);
        System.out.println("Request Body: " + jsonBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/quiz/submit"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("\n=== SUBMISSION RESULT ===");
        System.out.println("HTTP Status: " + response.statusCode());
        System.out.println("Response: " + response.body());

        JsonNode result = mapper.readTree(response.body());

        // HTTP 200 or 201 = accepted by server
        if (response.statusCode() == 200 || response.statusCode() == 201) {
            if (result.has("isCorrect")) {
                boolean correct = result.get("isCorrect").asBoolean();
                System.out.println(
                        correct ? "\n✅ SUCCESS! Leaderboard is CORRECT!" : "\n❌ INCORRECT - Check your logic.");
            } else {
                // API returned 201 without isCorrect — submission accepted
                System.out.println("\n✅ SUBMISSION ACCEPTED (HTTP " + response.statusCode() + ")");
                if (result.has("submittedTotal")) {
                    System.out.println("   Submitted Total : " + result.get("submittedTotal").asInt());
                }
                if (result.has("totalPollsMade")) {
                    System.out.println("   Total Polls Made: " + result.get("totalPollsMade").asInt());
                }
                if (result.has("attemptCount")) {
                    System.out.println("   Attempt Count   : " + result.get("attemptCount").asInt());
                }
            }
        } else {
            System.out.println("\n❌ SUBMISSION FAILED - HTTP " + response.statusCode());
        }
    }
}