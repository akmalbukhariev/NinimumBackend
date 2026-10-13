package com.ninimum.api.deliveryapp.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReturnPhotoServiceTest {
    @Test void missingPhotoCannotCreateEvidence() {
        var jdbc = mock(JdbcTemplate.class);
        var service = new ReturnPhotoService(jdbc);
        var ex = assertThrows(IllegalArgumentException.class, () -> service.save(1, "COURIER1", "Customer refused", null));
        assertEquals("RETURN_PHOTO_REQUIRED", ex.getMessage());
        verifyNoInteractions(jdbc);
    }

    @Test void malformedBase64CannotReachDatabase() {
        var jdbc = mock(JdbcTemplate.class);
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new ReturnPhotoService(jdbc).save(1, "COURIER1", "Customer refused", "not an image!"));
        assertEquals("RETURN_PHOTO_INVALID", ex.getMessage());
        verifyNoInteractions(jdbc);
    }

    @Test void pngIsConvertedToBoundedJpegWithWhiteTransparency() throws Exception {
        var source = new BufferedImage(2048, 1024, BufferedImage.TYPE_INT_ARGB);
        var input = new ByteArrayOutputStream();
        ImageIO.write(source, "png", input);
        byte[] stored = ReturnPhotoService.normalize(input.toByteArray());
        assertEquals(0xff, stored[0] & 0xff);
        assertEquals(0xd8, stored[1] & 0xff);
        var result = ImageIO.read(new ByteArrayInputStream(stored));
        assertEquals(1024, result.getWidth());
        assertEquals(512, result.getHeight());
        assertEquals(0xffffff, result.getRGB(0, 0) & 0xffffff);
    }

    @Test void arbitraryBytesCannotBeStoredAsAnImage() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> ReturnPhotoService.normalize("<script>invalid</script>".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("RETURN_PHOTO_INVALID", ex.getMessage());
    }

    @Test void retryCannotReplaceExistingEvidence() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList("SELECT reason,upload_sha256 FROM delivery_return_photos WHERE delivery_job_id=?", 1L))
                .thenReturn(java.util.List.of(java.util.Map.of("reason", "Customer refused", "upload_sha256", "original")));
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new ReturnPhotoService(jdbc).save(1, "COURIER1", "Changed reason", "AQ=="));
        assertEquals("RETURN_PHOTO_ALREADY_SAVED", ex.getMessage());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
