package com.fundmessenger.ai.service;

import com.fundmessenger.config.GeminiClient;
import com.fundmessenger.config.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Gemini Flash-Lite — PDF 직접 전송으로 구조화 텍스트 추출 (Step 1)
 *
 * v2 (2026-03-17): PNG 변환 제거, PDF 직접 업로드
 * - 이전: PDF → PDFBox PNG 200DPI → Gemini (폰트 깨짐 PDF 우회)
 * - 현재: PDF → Gemini 직접 전송 (속도 3배 향상, Step 0 제거)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private final GeminiClient geminiClient;

    private static final String EXTRACT_PROMPT_TEMPLATE = """
            분석 대상: %s
            작성자: %s

            이 PDF의 핵심 내용을 구조화된 텍스트로 추출하라.

            ## 추출 규칙
            - 분석 대상 기업명을 첫 줄에 명시하라
            - 모든 수치, 날짜, 고유명사는 원문 그대로 정확히 보존 (단위 포함)
            - 표/차트 데이터는 마크다운 테이블로 변환
            - 핵심 논지, 투자 포인트, 리스크 요인을 섹션으로 구분
            - 불필요한 서식(페이지 번호, 헤더/푸터, 면책조항)은 제거
            - PDF에 없는 내용을 추가하지 마라""";

    /**
     * PDF에서 텍스트 추출 (PDF 바이트를 Gemini에 직접 전송)
     * @param pdfBytes PDF 파일 바이트 배열
     * @param fileName 원본 파일명 (종목 식별용)
     * @param authorName 작성자명
     * @return 추출된 구조화 텍스트
     */
    public String extractText(byte[] pdfBytes, String fileName, String authorName) {
        if (!geminiClient.isAvailable()) {
            throw new ExternalServiceException("Gemini", "API 키가 설정되지 않았습니다");
        }

        String prompt = String.format(EXTRACT_PROMPT_TEMPLATE, fileName, authorName);
        log.info("Gemini PDF 텍스트 추출 시작: {} ({}KB)", fileName, pdfBytes.length / 1024);

        String result = geminiClient.generateWithPdf(pdfBytes, prompt, 2);
        if (result == null || result.isBlank()) {
            throw new ExternalServiceException("Gemini", "텍스트 추출 실패: 응답이 비어있습니다");
        }

        log.info("Gemini 텍스트 추출 완료: {}자", result.length());
        return result;
    }
}
