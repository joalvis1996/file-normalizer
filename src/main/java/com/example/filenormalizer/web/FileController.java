package com.example.filenormalizer.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class FileController {

    @GetMapping("/")
    public ResponseEntity<String> home() {
        return ResponseEntity.ok("""
            <html><head><meta charset='utf-8'><title>OK</title></head>
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
}
