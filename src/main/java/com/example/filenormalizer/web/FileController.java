// src/main/java/com/example/filenormalizer/web/FileController.java
package com.example.filenormalizer.web;

import com.example.filenormalizer.util.FilenameSanitizer;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
public class FileController {

    @GetMapping("/")
    public ResponseEntity<String> home() {
        return ResponseEntity.ok("""
            <html><head><meta charset='utf-8'><title>Filename Normalizer</title>
            <style>body{font:20px/1.4 system-ui;padding:40px}h1{font-size:42px;margin:0 0 24px}</style>
            </head><body>
              <h1>파일명 깨짐 방지 (Mac → Windows)</h1>
              <form method='post' enctype='multipart/form-data' action='/normalize'>
                <p><input type='file' name='files' multiple required></p>
                <p><label><input type='checkbox' name='forceZip' value='true'> 단일 파일이어도 ZIP으로 받기</label></p>
                <p><button>변환 & 다운로드</button></p>
              </form>
            </body></html>
            """);
    }

    // ✅ consumes를 multipart/form-data로 명시
    @PostMapping(path = "/normalize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ByteArrayResource> normalize(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "forceZip", required = false, defaultValue = "false") boolean forceZip
    ) throws IOException {

        if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
            return ResponseEntity.badRequest().body(null);
        }

        // 파일명 정규화
        List<String> sanitized = new ArrayList<>();
        for (MultipartFile f : files) {
            String orig = StringUtils.cleanPath(Objects.requireNonNullElse(f.getOriginalFilename(), "unnamed"));
            sanitized.add(FilenameSanitizer.sanitize(orig));
        }
        sanitized = FilenameSanitizer.dedupe(sanitized);

        // 단일 파일: 바로 다운로드
        if (sanitized.size() == 1 && !forceZip) {
            byte[] content = files.get(0).getBytes();
            String filename = sanitized.get(0);
            return ResponseEntity.ok()
                    .headers(dispositionHeaders(filename))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(content.length)
                    .body(new ByteArrayResource(content));
        }

        // 여러 파일 또는 ZIP 강제: ZIP으로 묶기
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            for (int i = 0; i < files.size(); i++) {
                MultipartFile f = files.get(i);
                if (f.isEmpty()) continue;
                ZipEntry entry = new ZipEntry(sanitized.get(i));
                zos.putNextEntry(entry);
                zos.write(f.getBytes());
                zos.closeEntry();
            }
        }
        byte[] zip = bos.toByteArray();

        return ResponseEntity.ok()
                .headers(dispositionHeaders("normalized_files.zip"))
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(zip.length)
                .body(new ByteArrayResource(zip));
    }

    private HttpHeaders dispositionHeaders(String filename) {
    HttpHeaders headers = new HttpHeaders();

    // ASCII fallback
    String fallback = filename.replaceAll("[^A-Za-z0-9._-]", "_");
    if (fallback.isEmpty()) fallback = "download";

    // UTF-8 인코딩 (공백은 %20)
    String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

    // Content-Disposition: 단 한 번만 set()
    String value = "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
    headers.set(HttpHeaders.CONTENT_DISPOSITION, value);

    headers.set("X-Content-Type-Options", "nosniff");
    return headers;
}

}
