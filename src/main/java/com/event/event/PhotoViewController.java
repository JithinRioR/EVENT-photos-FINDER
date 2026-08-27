package com.event.event;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class PhotoViewController {

    @GetMapping("/photos/{fileName}")
    public ResponseEntity<Resource> getPhoto(
            @PathVariable String fileName) {

        try {

            Path filePath =
                    Paths.get("uploads").resolve(fileName).normalize();

            Resource resource =
                    new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {

                return ResponseEntity.ok()
                        .header(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "inline; filename=\"" +
                                resource.getFilename() +
                                "\""
                        )
                        .body(resource);
            }

            return ResponseEntity.notFound().build();

        } catch (Exception e) {

            return ResponseEntity.internalServerError().build();
        }
    }
}