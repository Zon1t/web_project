package com.sseukbap.review.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 리뷰 사진 저장·삭제 (F-09)
 *
 * - 저장 폴더: {upload.dir}/reviews/  (application.yml의 upload.dir, 기본 'uploads', Git 제외)
 * - 파일 이름: 랜덤(UUID) + 확장자. 사용자가 올린 원래 이름은 쓰지 않는다. (이상한 글자·경로·이름 겹침 방지)
 * - DB IMAGE_PATH와 응답 imageUrl: /uploads/reviews/{파일 이름}  (WebConfig가 이 주소로 파일을 보여 준다)
 * - 검사: 확장자(jpg·jpeg·png) + MIME 타입(image/jpeg·image/png) + 5MB 이하 + 파일 앞부분이 진짜 jpg·png인지
 */
@Slf4j
@Component
public class ReviewImageStorage {

    static final String URL_PREFIX = "/uploads/reviews/";
    private static final String SUB_DIR = "reviews";
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    // 우리가 지은 이름 모양만 삭제한다 (다른 파일을 지우는 사고 방지)
    private static final Pattern STORED_NAME = Pattern.compile("^[0-9a-f]{32}\\.(jpg|png)$");

    // 파일 맨 앞 바이트 (매직 넘버)
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final Path directory;

    public ReviewImageStorage(@Value("${upload.dir}") String uploadDir) {
        this.directory = Path.of(uploadDir).resolve(SUB_DIR).toAbsolutePath().normalize();
    }

    /**
     * 사진을 검사하고 저장한다.
     *
     * @return 저장한 사진 주소 (/uploads/reviews/...)
     * @throws BusinessException INVALID_IMAGE (형식·용량 오류)
     */
    public String save(MultipartFile image) {
        ImageType type = validate(image);

        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + type.extension;
        try {
            Files.createDirectories(directory);
            try (InputStream in = image.getInputStream()) {
                Files.copy(in, directory.resolve(fileName));
            }
        } catch (IOException e) {
            log.error("리뷰 사진 저장 실패: {}", fileName, e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return URL_PREFIX + fileName;
    }

    /** 사진 파일 삭제. 실패하거나 우리가 저장한 모양이 아니면 로그만 남긴다. (응답은 성공) */
    public void delete(String imagePath) {
        if (imagePath == null || !imagePath.startsWith(URL_PREFIX)) {
            return;
        }
        String fileName = imagePath.substring(URL_PREFIX.length());
        if (!STORED_NAME.matcher(fileName).matches()) {
            log.warn("리뷰 사진 삭제 건너뜀 (저장 형식이 아님): {}", imagePath);
            return;
        }
        try {
            Files.deleteIfExists(directory.resolve(fileName));
        } catch (IOException e) {
            log.warn("리뷰 사진 삭제 실패: {} ({})", imagePath, e.getMessage());
        }
    }

    // ===== 검사 =====

    private ImageType validate(MultipartFile image) {
        if (image.isEmpty() || image.getSize() > MAX_BYTES) {
            throw invalid();
        }

        ImageType byExtension = ImageType.fromExtension(extensionOf(image.getOriginalFilename()));
        ImageType byMime = ImageType.fromMime(image.getContentType());
        if (byExtension == null || byExtension != byMime) {
            throw invalid();
        }

        if (!startsWith(image, byExtension.signature)) {
            throw invalid(); // 이름만 .jpg로 바꾼 다른 파일
        }
        return byExtension;
    }

    private boolean startsWith(MultipartFile image, byte[] signature) {
        try (InputStream in = image.getInputStream()) {
            byte[] head = in.readNBytes(signature.length);
            return Arrays.equals(head, signature);
        } catch (IOException e) {
            return false;
        }
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return (dot < 0) ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_IMAGE);
    }

    /** 받을 수 있는 사진 종류 */
    private enum ImageType {
        JPEG("jpg", "image/jpeg", JPEG_SIGNATURE),
        PNG("png", "image/png", PNG_SIGNATURE);

        private final String extension;
        private final String mime;
        private final byte[] signature;

        ImageType(String extension, String mime, byte[] signature) {
            this.extension = extension;
            this.mime = mime;
            this.signature = signature;
        }

        static ImageType fromExtension(String extension) {
            return switch (extension) {
                case "jpg", "jpeg" -> JPEG;
                case "png" -> PNG;
                default -> null;
            };
        }

        static ImageType fromMime(String contentType) {
            if (contentType == null) {
                return null;
            }
            String mime = contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
            for (ImageType type : values()) {
                if (type.mime.equals(mime)) {
                    return type;
                }
            }
            return null;
        }
    }
}
