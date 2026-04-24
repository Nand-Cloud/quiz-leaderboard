# Quiz Leaderboard System — SRM Internship Assignment

A Java application that polls a quiz validator API 10 times, deduplicates events, aggregates scores, and submits a correct leaderboard.

---

## Problem Summary

- Poll `/quiz/messages` **10 times** (poll index 0–9) with a **5-second delay** between each call
- The same event data may appear in multiple polls → **must deduplicate**
- Deduplication key: `roundId + participant`
- Aggregate scores per participant after deduplication
- Sort leaderboard by `totalScore` descending
- Submit leaderboard **once** via POST `/quiz/submit`

---

## Project Structure

```
quiz-leaderboard/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── com/quiz/
                ├── Main.java          ← Entry point (set your regNo here)
                └── QuizService.java   ← All business logic
```

---

## Prerequisites

- Java 11 or higher
- Maven 3.6+

---

## Setup & Run

### 1. Clone the repository
```bash
git clone https://github.com/your-username/quiz-leaderboard.git
cd quiz-leaderboard
```

### 2. Set your Registration Number
Open `src/main/java/com/quiz/Main.java` and replace:
```java
String regNo = "RA2311003011580"; // <-- Replace with YOUR reg number
```

### 3. Build the project
```bash
mvn clean package
```

### 4. Run the application
```bash
java -jar target/quiz-leaderboard-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## How It Works (Step by Step)

```
┌─────────────────────────────────────────────────────────────────┐
│                     QUIZ LEADERBOARD FLOW                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. POLL API 10 times (poll=0 to poll=9)                        │
│     GET /quiz/messages?regNo=<regNo>&poll=<n>                   │
│     → 5-second delay between each call (mandatory)              │
│                                                                  │
│  2. COLLECT all events from all polls                           │
│     Example: { roundId:"R1", participant:"Alice", score:10 }   │
│                                                                  │
│  3. DEDUPLICATE using key = roundId + "|" + participant         │
│     If same (roundId, participant) seen again → SKIP            │
│                                                                  │
│  4. AGGREGATE scores per participant                            │
│     Alice: R1(10) + R2(30) = 40                                 │
│                                                                  │
│  5. SORT leaderboard by totalScore descending                   │
│                                                                  │
│  6. SUBMIT once via POST /quiz/submit                           │
│     { "regNo": "...", "leaderboard": [...] }                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Why Deduplication Matters

In distributed systems, the same API response can be delivered multiple times across polls.

| Without Deduplication ❌ | With Deduplication ✅ |
|--------------------------|----------------------|
| Poll 1 → Alice +10       | Poll 1 → Alice +10   |
| Poll 3 → Alice +10 again | Poll 3 → SKIPPED     |
| **Total = 20 (WRONG)**   | **Total = 10 ✅**    |

The unique key is `(roundId, participant)` — if the same combination appears again, it's a duplicate and must be ignored.

---

## Sample Output

```
=== Quiz Leaderboard System ===
RegNo: RA2311003011580

Polling 0/9...
  → Got 4 events
Polling 1/9...
  [DUPLICATE SKIPPED] roundId=R1 participant=Alice
  → Got 4 events
...

Total raw events collected: 40
Duplicates removed: 12
Unique events: 28

=== LEADERBOARD ===
  #1 Bob                  120 pts
  #2 Alice                100 pts
----------------------------
  Grand Total Score: 220

Submitting leaderboard...

=== SUBMISSION RESULT ===
HTTP Status: 200
Response: {"isCorrect":true,"isIdempotent":true,"submittedTotal":220,"expectedTotal":220,"message":"Correct!"}

✅ SUCCESS! Leaderboard is CORRECT!
```

---

## Technologies Used

- **Java 11** — HttpClient (built-in, no extra HTTP library needed)
- **Jackson 2.15** — JSON parsing and serialization
- **Maven** — Build and dependency management

---

## Key Design Decisions

| Decision | Reason |
|----------|--------|
| `LinkedHashMap` for deduplication | Preserves insertion order while providing O(1) lookups |
| `roundId + "\|" + participant` as key | Composite key exactly matching the problem spec |
| `merge()` for score aggregation | Clean, concise way to sum scores per participant |
| Submit only once | Prevents idempotency issues |

---

## Author

- **Name**: Nand Kishor Singh  
- **Reg No**: RA2311028010070
- **College**: SRM Institute of Science and Technology
#
