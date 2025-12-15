package com._OK.controller;

import com._OK.dto.response.ProblemResponseDto;
import com._OK.service.LearningService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/learning")
public class LearningController {

    private final LearningService learningService;

    /**
     * 오답 기반 집중학습 문제 생성 API
     * POST /api/learning/generate-focus-questions
     */
    @PostMapping("/generate-focus-questions")
    public ResponseEntity<?> generateFocusQuestions(@RequestBody FocusRequest request) {
        try {
            // ✅ Service가 ErrorStat 타입을 모르므로, Map 형태로 그대로 전달
            List<ProblemResponseDto> generated =
                    learningService.generateFocusQuestions(request.getError_stats());

            return ResponseEntity.ok(generated);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    new ErrorResponse(
                            500,
                            "FastAPI 서버와의 통신에 실패했습니다: " + e.getMessage(),
                            "/api/learning/generate-focus-questions"
                    )
            );
        }
    }

    @Getter
    public static class FocusRequest {
        // ✅ error_stats를 "그냥 JSON 객체 리스트"로 받는다.
        // 예: [{"problemId":1,"wrong_rate":0.7}, ...]
        private List<Map<String, Object>> error_stats;

        // FastAPI payload에서 model도 받도록 되어 있지만, 지금은 Service에서 기본값 사용해도 됨
        private String model = "gpt-4o-mini";
    }

    // ✅ ErrorResponse 타입 에러 해결: 컨트롤러 내부 클래스로 추가
    @Getter
    @AllArgsConstructor
    public static class ErrorResponse {
        private int status;
        private String message;
        private String path;
    }
}
