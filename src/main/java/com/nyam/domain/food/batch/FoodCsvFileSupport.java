package com.nyam.domain.food.batch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 로컬 경로를 메시지에 노출하지 않고 strict UTF-8 읽기와 checksum 검증을 지원합니다.
 */
public final class FoodCsvFileSupport {

    /**
     * 인스턴스 생성을 막는 파일 유틸리티 생성자입니다.
     */
    private FoodCsvFileSupport() {
    }

    /**
     * 잘못된 UTF-8 바이트를 치환하지 않고 실패시키는 스트리밍 Reader를 엽니다.
     *
     * @param path 읽을 CSV 경로
     * @return strict UTF-8 BufferedReader
     * @throws IOException 파일을 열 수 없는 경우
     */
    public static BufferedReader openStrictUtf8(Path path) throws IOException {
        return new BufferedReader(new InputStreamReader(
                Files.newInputStream(path),
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)));
    }

    /**
     * 파일의 SHA-256을 소문자 16진수로 계산합니다.
     *
     * @param path checksum을 계산할 파일
     * @return 소문자 SHA-256 문자열
     * @throws IOException 파일을 읽지 못한 경우
     */
    public static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var stream = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * 한 헤더 행에서 선행 BOM을 제거하고 정확한 45개 필드 계약을 확인합니다.
     *
     * @param headerLine CSV 첫 물리 행
     * @throws FoodImportException 헤더가 없거나 승인된 구조와 다른 경우
     */
    public static void requireExactHeader(String headerLine) {
        if (headerLine == null) {
            throw new FoodImportException("Food CSV header is missing");
        }
        String withoutBom = headerLine.startsWith("\uFEFF") ? headerLine.substring(1) : headerLine;
        List<String> fields = FoodCsvParser.parseLine(withoutBom);
        FoodCsvSchema.requireExactHeader(fields);
    }
}
