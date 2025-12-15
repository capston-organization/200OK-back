package com._OK.service;

import com._OK.domain.Problem;
import com._OK.dto.response.ProblemResponseDto;
import com._OK.repository.ProblemRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearningService {

    private final ProblemRepository problemRepository;

    @Value("${fastapi.url}")
    private String fastApiUrl;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
                .baseUrl(fastApiUrl)
                .build();
    }

    /**
     * FastAPI에 틀린 문제를 보내서
     * 집중학습 문제를 새로 생성하는 함수
     *
     * ⚠️ Controller의 내부 클래스(ErrorStat)에 의존하지 않기 위해
     * stats를 "원시 Map 형태"로 받아서 FastAPI payload로 그대로 전달한다.
     */
    public List<ProblemResponseDto> generateFocusQuestions(List<Map<String, Object>> stats) {

        try {
            // 0️⃣ problemId 목록 추출 (wrongProblemIds 에러 해결)
            List<Long> wrongProblemIds = stats.stream()
                    .map(m -> {
                        Object v = m.get("problemId");
                        if (v == null) return null;
                        if (v instanceof Number) return ((Number) v).longValue();
                        return Long.valueOf(String.valueOf(v));
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // 1️⃣ 기존 문제 DB에서 가져오기
            List<Problem> wrongProblems = problemRepository.findAllById(wrongProblemIds);

            // 2️⃣ FastAPI 요청 Body 구성
            // FastAPI는 error_stats + model 을 받는 형태이므로, 그 구조 그대로 맞춰서 보낸다.
            FocusRequest request = new FocusRequest(stats, "gpt-4o-mini");

            // 3️⃣ FastAPI 호출
            FocusApiResponse response = webClient.post()
                    .uri("/api/learning/generate-focus-questions/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(FocusApiResponse.class)
                    .timeout(Duration.ofMinutes(3))
                    .block();

            if (response == null || response.getGenerated_questions() == null) {
                throw new RuntimeException("FastAPI 응답 없음");
            }

            // 4️⃣ 새 문제 DB 저장
            List<Problem> newProblems = response.getGenerated_questions().stream()
                    .map(g -> Problem.builder()
                            .question(g.getQuestion())
                            .answer(g.getAnswer())
                            .type(g.getType())
                            .difficulty("focus")
                            .language("ko")
                            .createdAt(LocalDateTime.now())
                            .build())
                    .collect(Collectors.toList());

            problemRepository.saveAll(newProblems);

            // 5️⃣ 원하는 DTO로 변환
            return newProblems.stream()
                    .map(p -> ProblemResponseDto.builder()
                            .problemId(p.getProblemId())
                            .question(p.getQuestion())
                            .answer(p.getAnswer())
                            .type(p.getType())
                            .difficulty(p.getDifficulty())
                            .build())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new RuntimeException("FastAPI 통신 실패: " + e.getMessage(), e);
        }
    }

    // ✅ FastAPI 집중학습 요청 DTO (FastAPI payload 구조 그대로)
    @Getter
    public static class FocusRequest {
        private final List<Map<String, Object>> error_stats;
        private final String model;

        public FocusRequest(List<Map<String, Object>> error_stats, String model) {
            this.error_stats = error_stats;
            this.model = model;
        }
    }

    // ✅ FastAPI 응답 DTO
    @Getter
    public static class FocusApiResponse {
        private int question_count;
        private List<GeneratedQuestion> generated_questions;

        @Getter
        public static class GeneratedQuestion {
            private String type;
            private String question;
            private String answer;
        }
    }
}
