package com.quiz;

public class Main {
    public static void main(String[] args) throws Exception {
        String regNo = "RA2311028010070";

        QuizService service = new QuizService(regNo);
        service.run();
    }
}
