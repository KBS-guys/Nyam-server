package com.nyam.deployment.smoke;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 한 배포 대상을 식별하는 합성 사용자 A/B ID manifest의 엄격한 파일 계약입니다.
 */
public final class SmokeSeedManifest {

    /** 외부 메일이 전달되지 않는 예약 도메인의 사용자 A 주소입니다. */
    public static final String USER_A_EMAIL = "nyamlog-smoke-a@example.invalid";

    /** 외부 메일이 전달되지 않는 예약 도메인의 사용자 B 주소입니다. */
    public static final String USER_B_EMAIL = "nyamlog-smoke-b@example.invalid";

    private static final Pattern TARGET_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private SmokeSeedManifest() {
    }

    /**
     * 대상 이름과 데이터베이스가 생성한 A/B ID를 새 사용자 전용 파일에 기록합니다.
     *
     * @param output 새 manifest 절대 경로
     * @param target 사용자가 구분하는 비민감 대상 이름
     * @param users 검증된 A/B 사용자 ID
     * @throws IOException 안전한 파일로 기록할 수 없는 경우
     */
    public static void write(Path output, String target, Users users) throws IOException {
        validateTarget(target);
        validateUsers(users);
        String contents = String.join("\n",
                "format=1",
                "target=" + target,
                "userA.id=" + users.userAId(),
                "userB.id=" + users.userBId(),
                "");
        PrivateArtifactFile.writeNew(output, contents);
    }

    /**
     * 사용자 전용 manifest를 읽고 요청 대상 및 A/B ID 계약을 검증합니다.
     *
     * @param input manifest 절대 경로
     * @param expectedTarget JWT를 발급할 대상 이름
     * @return 검증된 A/B 사용자 ID
     * @throws IOException 파일을 안전하게 읽을 수 없는 경우
     */
    public static Users read(Path input, String expectedTarget) throws IOException {
        validateTarget(expectedTarget);
        List<String> lines = PrivateArtifactFile.read(input).lines().toList();
        if (lines.size() != 4
                || !lines.get(0).equals("format=1")
                || !lines.get(1).equals("target=" + expectedTarget)
                || !lines.get(2).startsWith("userA.id=")
                || !lines.get(3).startsWith("userB.id=")) {
            throw new IllegalStateException("Smoke seed manifest contract is invalid");
        }
        try {
            Users users = new Users(
                    Long.parseLong(lines.get(2).substring("userA.id=".length())),
                    Long.parseLong(lines.get(3).substring("userB.id=".length())));
            validateUsers(users);
            return users;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Smoke seed manifest IDs are invalid", exception);
        }
    }

    private static void validateTarget(String target) {
        if (target == null || !TARGET_PATTERN.matcher(target).matches()) {
            throw new IllegalArgumentException("NYAM_SMOKE_TARGET has an invalid format");
        }
    }

    private static void validateUsers(Users users) {
        if (users == null || users.userAId() <= 0 || users.userBId() <= 0
                || users.userAId() == users.userBId()) {
            throw new IllegalArgumentException("Smoke user IDs must be distinct positive values");
        }
    }

    /**
     * 데이터베이스가 생성하고 seed 검증을 통과한 사용자 A/B 식별자입니다.
     *
     * @param userAId 사용자 A의 내부 ID
     * @param userBId 사용자 B의 내부 ID
     */
    public record Users(long userAId, long userBId) {
    }
}
