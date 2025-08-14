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
            <html><head><meta charset='utf-8'><title>Filename Normalizer</title></head>
            <body style='font-family:system-ui;padding:24px'>
              <h1>파일명 깨짐 방지 (Mac → Windows)</h1>
              <form method='post' enctype='multipart/form-data' action='/normalize'>
                <p><input type='file' name='files' multiple required></p>
                <p><label><input type='checkbox' name='forceZip' value='true'> 단일 파일이어도 ZIP으로 받기</label></p>
                <p><button>변환 & 다운로드</button></p>
              </form>
            </body></html>
            """);
    }

    @PostMapping("/normalize")
    public ResponseEntity<ByteArrayResource> normalize(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "forceZip", required = false) boolean forceZip
    ) throws IOException {

        if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
            return ResponseEntity.badRequest().body(null);
        }

        // 1) 업로드된 원본 이름 정리
        List<String> sanitized = new ArrayList<>();
        for (MultipartFile f : files) {
            String orig = StringUtils.cleanPath(Objects.requireNonNullElse(f.getOriginalFilename(), "unnamed"));
            sanitized.add(FilenameSanitizer.sanitize(orig));
        }
        sanitized = FilenameSanitizer.dedupe(sanitized);

        // 2) 단일 파일 + ZIP 강제 아님 → 바로 파일 반환
        if (sanitized.size() == 1 && !forceZip) {
            byte[] content = files.get(0).getBytes();
            String filename = sanitized.get(0);
            return ResponseEntity.ok()
                    .headers(dispositionHeaders(filename))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(content.length)
                    .body(new ByteArrayResource(content));
        }

        // 3) ZIP으로 묶어서 반환 (UTF-8 엔트리명, Windows 10+에서 정상 표시)
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
        String fallback = asciiFallback(filename);
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+","%20");

        ContentDisposition cd = ContentDisposition.attachment()
                .filename(fallback)
                .build();

        headers.setContentDisposition(cd);
        // RFC 6266: filename* 추가(UTF-8)
        headers.add(HttpHeaders.CONTENT_DISPOSITION, cd.toString() + "; filename*=UTF-8''" + encoded);
        headers.set(HttpHeaders.X_CONTENT_TYPE_OPTIONS, "nosniff");
        return headers;
    }

    private String asciiFallback(String s) {
        String out = s.replaceAll("[^A-Za-z0-9._-]", "_");
        return out.isEmpty() ? "download" : out;
    }
}