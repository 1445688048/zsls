// FileController.java
// 受控文件下载：属主校验 + 文件名白名单，防止路径穿越
package com.palmlawyer.controller;

import com.palmlawyer.service.CaseGuard;
import com.palmlawyer.util.AuthContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件下载控制器
 *
 * <p>路径：/files/{caseId}/{filename}，仅允许下载本案件目录下的文件。
 */
@RestController
@RequestMapping("/files")
public class FileController {

    private final CaseGuard caseGuard;

    @Value("${palmlawyer.storage.local-path}")
    private String storagePath;

    public FileController(CaseGuard caseGuard) {
        this.caseGuard = caseGuard;
    }

    @GetMapping("/{caseId}/{filename}")
    public ResponseEntity<Resource> download(@PathVariable Long caseId, @PathVariable String filename) {
        caseGuard.assertOwner(caseId, AuthContext.userId());

        // 文件名白名单：仅允许字母数字、下划线、点、连字符，杜绝路径穿越
        if (filename == null || !filename.matches("[A-Za-z0-9._-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法文件名");
        }

        Path file = Paths.get(storagePath, String.valueOf(caseId), filename).toAbsolutePath().normalize();
        Path base = Paths.get(storagePath, String.valueOf(caseId)).toAbsolutePath().normalize();
        if (!file.startsWith(base) || !Files.exists(file) || !Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
        }

        MediaType mediaType = guessMediaType(filename);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(new FileSystemResource(file));
    }

    private MediaType guessMediaType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (lower.endsWith(".mp4")) return MediaType.parseMediaType("video/mp4");
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
