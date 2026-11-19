package com.sseukbap.review.service;

import com.sseukbap.common.exception.BusinessException;
import com.sseukbap.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 리뷰 사진 검사·저장·삭제. 임시 폴더(@TempDir)에 저장하고 끝나면 지워진다. */
class ReviewImageStorageTest {

    // 진짜 jpg·png 파일의 맨 앞 바이트 + 아무 내용
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};

    @TempDir Path uploadDir;
    ReviewImageStorage storage;

    @BeforeEach
    void setUp() {
        storage = new ReviewImageStorage(uploadDir.toString());
    }

    @Test
    void jpg와_png를_랜덤_이름으로_저장한다() {
        String jpgPath = storage.save(new MockMultipartFile("image", "국밥 사진.JPG", "image/jpeg", JPEG));
        String pngPath = storage.save(new MockMultipartFile("image", "a.png", "image/png", PNG));
        String jpegPath = storage.save(new MockMultipartFile("image", "b.jpeg", "image/jpeg", JPEG));

        assertThat(jpgPath).matches("/uploads/reviews/[0-9a-f]{32}\\.jpg");
        assertThat(pngPath).matches("/uploads/reviews/[0-9a-f]{32}\\.png");
        assertThat(jpegPath).endsWith(".jpg");
        assertThat(storedFile(jpgPath)).exists().hasBinaryContent(JPEG);
        assertThat(storedFile(pngPath)).exists();
    }

    @Test
    void 형식이나_용량이_맞지_않으면_INVALID_IMAGE() {
        assertInvalid(new MockMultipartFile("image", "a.gif", "image/gif", JPEG));          // 확장자
        assertInvalid(new MockMultipartFile("image", "a.jpg", "text/plain", JPEG));         // MIME
        assertInvalid(new MockMultipartFile("image", "a.jpg", "image/png", PNG));           // 확장자와 MIME이 다름
        assertInvalid(new MockMultipartFile("image", "a.jpg", "image/jpeg", "hello".getBytes())); // 이름만 바꾼 파일
        assertInvalid(new MockMultipartFile("image", "a.png", "image/png", JPEG));          // 내용은 jpg
        assertInvalid(new MockMultipartFile("image", "noext", "image/jpeg", JPEG));         // 확장자 없음
        assertInvalid(new MockMultipartFile("image", "a.jpg", "image/jpeg", new byte[0]));  // 빈 파일

        byte[] tooBig = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(JPEG, 0, tooBig, 0, JPEG.length);
        assertInvalid(new MockMultipartFile("image", "big.jpg", "image/jpeg", tooBig));     // 5MB 초과

        assertThat(uploadDir.resolve("reviews")).doesNotExist(); // 실패하면 아무것도 저장하지 않는다
    }

    @Test
    void 저장한_사진만_지운다() throws IOException {
        String path = storage.save(new MockMultipartFile("image", "a.jpg", "image/jpeg", JPEG));
        Path outside = Files.writeString(uploadDir.resolve("keep.jpg"), "다른 파일");

        storage.delete(path);
        storage.delete("/uploads/reviews/../keep.jpg");  // 다른 폴더로 빠져나가는 경로
        storage.delete("/uploads/other/keep.jpg");
        storage.delete(null);
        storage.delete("/uploads/reviews/" + "0".repeat(32) + ".jpg"); // 없는 파일도 오류 없이 지나간다

        assertThat(storedFile(path)).doesNotExist();
        assertThat(outside).exists();
    }

    private Path storedFile(String imagePath) {
        return uploadDir.resolve("reviews").resolve(imagePath.substring("/uploads/reviews/".length()));
    }

    private void assertInvalid(MockMultipartFile file) {
        assertThatThrownBy(() -> storage.save(file))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_IMAGE);
    }
}
