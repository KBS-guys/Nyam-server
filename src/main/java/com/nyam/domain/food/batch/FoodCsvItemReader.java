package com.nyam.domain.food.batch;

import java.io.BufferedReader;
import java.io.IOException;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;

/**
 * CSV를 물리 행 단위로 스트리밍하고 커밋된 데이터 행 위치를 재시작 상태로 저장합니다.
 */
public class FoodCsvItemReader implements ItemStreamReader<FoodCsvRow> {

    private static final String POSITION_KEY = "foodCsvItemReader.itemsRead";

    private final FoodImportInput input;
    private BufferedReader reader;
    private long itemsRead;

    /**
     * 현재 프로세스의 비영속 입력 경로를 주입받습니다.
     *
     * @param input CSV 입력 경로 보관자
     */
    public FoodCsvItemReader(FoodImportInput input) {
        this.input = input;
    }

    /**
     * 헤더를 다시 확인하고 마지막 커밋 체크포인트까지 물리 행을 이동합니다.
     *
     * @param executionContext 이전 실행의 커밋된 Reader 상태
     * @throws ItemStreamException 파일을 열거나 체크포인트를 복원할 수 없는 경우
     */
    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        try {
            reader = FoodCsvFileSupport.openStrictUtf8(input.requirePath());
            FoodCsvFileSupport.requireExactHeader(reader.readLine());
            itemsRead = executionContext.getLong(POSITION_KEY, 0L);
            for (long skipped = 0; skipped < itemsRead; skipped++) {
                if (reader.readLine() == null) {
                    throw new FoodImportException("Persisted Reader checkpoint exceeds the current input");
                }
            }
        } catch (IOException | RuntimeException exception) {
            closeQuietly();
            throw new ItemStreamException("Food CSV Reader could not restore its checkpoint");
        }
    }

    /**
     * 다음 물리 행을 엄격히 파싱하고 적재 대상 원본 필드로 변환합니다.
     *
     * @return 다음 데이터 행 또는 파일 끝의 {@code null}
     * @throws Exception UTF-8 또는 CSV 구조가 승인 계약을 위반한 경우
     */
    @Override
    public FoodCsvRow read() throws Exception {
        String line;
        try {
            line = reader.readLine();
        } catch (IOException exception) {
            throw new FoodImportException("Food CSV Reader could not read the input");
        }
        if (line == null) {
            return null;
        }
        FoodCsvRow row = FoodCsvRow.from(FoodCsvParser.parseLine(line));
        itemsRead++;
        return row;
    }

    /**
     * 현재까지 성공적으로 읽은 데이터 행 수를 다음 chunk 커밋 상태에 기록합니다.
     *
     * @param executionContext 현재 Step의 영속 실행 컨텍스트
     */
    @Override
    public void update(ExecutionContext executionContext) {
        executionContext.putLong(POSITION_KEY, itemsRead);
    }

    /**
     * 열려 있는 CSV 스트림을 닫습니다.
     *
     * @throws ItemStreamException 스트림을 닫지 못한 경우
     */
    @Override
    public void close() throws ItemStreamException {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (IOException exception) {
            throw new ItemStreamException("Food CSV Reader could not close");
        } finally {
            reader = null;
        }
    }

    /**
     * open 실패 중 생성된 스트림을 추가 예외 없이 정리합니다.
     */
    private void closeQuietly() {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {
                // 원래 open 실패를 유지합니다.
            } finally {
                reader = null;
            }
        }
    }
}
