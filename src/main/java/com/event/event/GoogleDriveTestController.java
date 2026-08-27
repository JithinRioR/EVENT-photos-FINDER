package com.event.event;

import com.google.api.services.drive.model.File;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GoogleDriveTestController {

    private final GoogleDriveService googleDriveService;

    public GoogleDriveTestController(GoogleDriveService googleDriveService) {
        this.googleDriveService = googleDriveService;
    }

    @GetMapping("/drive-photos")
    public String getPhotos() {

        try {

            List<File> files = googleDriveService.getDriveService()
                    .files()
                    .list()
                    .setQ("'1ETba7ZKTXc_M69X22UZV65EArYczahoW' in parents and trashed = false")
                    .setPageSize(50)
                    .setFields("files(id,name,mimeType,webViewLink)")
                    .execute()
                    .getFiles();

            if (files == null || files.isEmpty()) {
                return "No photos found in the event folder.";
            }

            StringBuilder result = new StringBuilder();

            result.append("<h2>Event Photos</h2>");

            for (File file : files) {

                result.append("<p>")
                        .append("📸 ")
                        .append(file.getName())
                        .append("<br>")
                        .append("Type: ")
                        .append(file.getMimeType())
                        .append("<br>")
                        .append("ID: ")
                        .append(file.getId())
                        .append("<br>")
                        .append("<a href='/drive-photo/")
                        .append(file.getId())
                        .append("' target='_blank'>")
                        .append("View Photo")
                        .append("</a>")
                        .append("</p>");
            }

            return result.toString();

        } catch (Exception e) {

            e.printStackTrace();

            return "Google Drive error: " + e.getMessage();
        }
    }

    // Download and display a photo from Google Drive
    @GetMapping("/drive-photo/{fileId}")
    public ResponseEntity<byte[]> getPhoto(
            @PathVariable String fileId) {

        try {

            byte[] photo =
                    googleDriveService.downloadFile(fileId);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(photo);

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .internalServerError()
                    .build();
        }
    }
}