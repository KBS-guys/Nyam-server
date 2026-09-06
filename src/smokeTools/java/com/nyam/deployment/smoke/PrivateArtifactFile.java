package com.nyam.deployment.smoke;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * seed manifest와 JWT를 새 사용자 전용 파일에만 기록하고 다시 읽습니다.
 */
public final class PrivateArtifactFile {

    private static final Set<PosixFilePermission> OWNER_READ_WRITE =
            PosixFilePermissions.fromString("rw-------");

    private PrivateArtifactFile() {
    }

    /**
     * 기존 파일을 덮어쓰지 않고 운영체제가 지원하는 사용자 전용 권한으로 내용을 기록합니다.
     *
     * @param output 새로 만들 절대 경로
     * @param contents 기록할 내용
     * @throws IOException 파일 생성·권한 제한·쓰기 중 하나라도 실패한 경우
     */
    public static void writeNew(Path output, String contents) throws IOException {
        Path normalized = requireAbsoluteFilePath(output);
        Path parent = normalized.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("Private output directory must already exist");
        }

        boolean created = false;
        try {
            if (Files.getFileStore(parent).supportsFileAttributeView(PosixFileAttributeView.class)) {
                Files.createFile(normalized, PosixFilePermissions.asFileAttribute(OWNER_READ_WRITE));
            } else if (Files.getFileStore(parent).supportsFileAttributeView(AclFileAttributeView.class)) {
                Files.createFile(normalized);
                restrictWindowsAcl(normalized);
            } else {
                throw new IOException("Private file permissions are not supported");
            }
            created = true;
            requirePrivate(normalized);
            Files.writeString(normalized, contents, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException exception) {
            if (created) {
                Files.deleteIfExists(normalized);
            }
            throw exception;
        }
    }

    /**
     * 사용자 전용 권한이 유지되는 일반 파일만 UTF-8로 읽습니다.
     *
     * @param input 읽을 절대 경로
     * @return 파일 내용
     * @throws IOException 파일이나 권한 계약이 유효하지 않은 경우
     */
    public static String read(Path input) throws IOException {
        Path normalized = requireAbsoluteFilePath(input);
        if (!Files.isRegularFile(normalized) || Files.isSymbolicLink(normalized)) {
            throw new IOException("Private input must be a regular non-symbolic file");
        }
        requirePrivate(normalized);
        return Files.readString(normalized, StandardCharsets.UTF_8);
    }

    private static Path requireAbsoluteFilePath(Path path) throws IOException {
        if (path == null || !path.isAbsolute()) {
            throw new IOException("Private artifact path must be absolute");
        }
        return path.normalize();
    }

    private static void restrictWindowsAcl(Path path) throws IOException {
        AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
        UserPrincipal owner = Files.getOwner(path);
        AclEntry ownerAccess = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .build();
        view.setAcl(List.of(ownerAccess));
    }

    private static void requirePrivate(Path path) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView(PosixFileAttributeView.class)) {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
            if (!OWNER_READ_WRITE.equals(permissions)) {
                throw new IOException("Private artifact has group or other permissions");
            }
            return;
        }

        AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (view == null) {
            throw new IOException("Private file permissions are not supported");
        }
        UserPrincipal owner = Files.getOwner(path);
        boolean ownerCanRead = false;
        boolean ownerCanWrite = false;
        for (AclEntry entry : view.getAcl()) {
            if (entry.type() != AclEntryType.ALLOW) {
                continue;
            }
            if (!entry.principal().equals(owner)) {
                throw new IOException("Private artifact grants access to another principal");
            }
            ownerCanRead |= entry.permissions().contains(AclEntryPermission.READ_DATA);
            ownerCanWrite |= entry.permissions().contains(AclEntryPermission.WRITE_DATA);
        }
        if (!ownerCanRead || !ownerCanWrite) {
            throw new IOException("Private artifact owner permissions are incomplete");
        }
    }
}
