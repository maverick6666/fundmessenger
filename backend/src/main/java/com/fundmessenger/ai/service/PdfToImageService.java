package com.fundmessenger.ai.service;

import com.fundmessenger.config.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PDFBox로 PDF 각 페이지를 200DPI PNG 이미지로 렌더링
 */
@Slf4j
@Service
public class PdfToImageService {

    private static final int DPI = 200;
    private static final int MAX_PAGES = 50;

    /** PDF byte[]를 PNG 바이트 배열 리스트로 변환 */
    public List<byte[]> renderPages(byte[] pdfBytes) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int pageCount = doc.getNumberOfPages();
            if (pageCount > MAX_PAGES) {
                throw new IllegalArgumentException(
                        "PDF 페이지 수 초과: " + pageCount + "페이지 (최대 " + MAX_PAGES + "페이지)");
            }

            log.info("PDF→PNG 변환 시작: {}페이지", pageCount);
            PDFRenderer renderer = new PDFRenderer(doc);
            List<byte[]> pages = new ArrayList<>();

            for (int i = 0; i < pageCount; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, DPI);
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", baos);
                    pages.add(baos.toByteArray());
                } finally {
                    image.flush();
                }
            }

            log.info("PDF→PNG 변환 완료: {}페이지", pageCount);
            return pages;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("PDF 이미지 변환 실패: " + e.getMessage());
        }
    }

    /** PDF byte[] 페이지 수 확인 */
    public int getPageCount(byte[] pdfBytes) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            return doc.getNumberOfPages();
        } catch (Exception e) {
            throw new BadRequestException("PDF 페이지 수 확인 실패: " + e.getMessage());
        }
    }

    /** PDF 파일 경로 기반 (로컬 개발용 하위호환) */
    public List<byte[]> renderPages(Path pdfPath) {
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            return renderPagesFromDoc(doc);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("PDF 이미지 변환 실패: " + e.getMessage());
        }
    }

    /** PDF 파일 경로 기반 페이지 수 확인 (로컬 개발용 하위호환) */
    public int getPageCount(Path pdfPath) {
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            return doc.getNumberOfPages();
        } catch (Exception e) {
            throw new BadRequestException("PDF 페이지 수 확인 실패: " + e.getMessage());
        }
    }

    private List<byte[]> renderPagesFromDoc(PDDocument doc) {
        int pageCount = doc.getNumberOfPages();
        if (pageCount > MAX_PAGES) {
            throw new IllegalArgumentException(
                    "PDF 페이지 수 초과: " + pageCount + "페이지 (최대 " + MAX_PAGES + "페이지)");
        }

        log.info("PDF→PNG 변환 시작: {}페이지", pageCount);
        PDFRenderer renderer = new PDFRenderer(doc);
        List<byte[]> pages = new ArrayList<>();

        for (int i = 0; i < pageCount; i++) {
            try {
                BufferedImage image = renderer.renderImageWithDPI(i, DPI);
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", baos);
                    pages.add(baos.toByteArray());
                } finally {
                    image.flush();
                }
            } catch (Exception e) {
                throw new BadRequestException("PDF 페이지 " + i + " 렌더링 실패: " + e.getMessage());
            }
        }

        log.info("PDF→PNG 변환 완료: {}페이지", pageCount);
        return pages;
    }
}
