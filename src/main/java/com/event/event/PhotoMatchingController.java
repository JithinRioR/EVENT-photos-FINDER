package com.event.event;

import com.google.api.services.drive.model.File;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/photos")
@CrossOrigin
public class PhotoMatchingController {

    private final PhotoMatchingService photoMatchingService;
    private final GoogleDriveService googleDriveService;

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    public PhotoMatchingController(
            PhotoMatchingService photoMatchingService,
            GoogleDriveService googleDriveService) {

        this.photoMatchingService =
                photoMatchingService;
        this.googleDriveService =
                googleDriveService;
    }

    // Admin login check
    @PostMapping("/admin/login")
    public ResponseEntity<Map<String, String>> adminLogin(
            @RequestParam("username") String username,
            @RequestParam("password") String password) {

        Map<String, String> response = new HashMap<>();

        if (adminUsername.equals(username) && adminPassword.equals(password)) {
            response.put("status", "success");
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "error");
            response.put("message", "Incorrect username or password");
            return ResponseEntity.status(401).body(response);
        }
    }

    // List all event folders
    @GetMapping("/folders")
    public List<File> listFolders() {
        try {
            return googleDriveService.getFolders();
        } catch (Exception e) {
            System.out.println("Error listing folders: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // List all files inside a chosen folder
    @GetMapping("/list")
    public List<File> listAllFiles(
            @RequestParam("folderId") String folderId) {
        try {
            return googleDriveService.getDriveFiles(folderId);
        } catch (Exception e) {
            System.out.println("Error listing files: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // Find matching photos by face in a chosen folder
    @PostMapping(
            value = "/find",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public List<File> findPhotos(
            @RequestParam("selfie") MultipartFile selfie,
            @RequestParam("folderId") String folderId)
            throws Exception {

        return photoMatchingService.findMatchingPhotos(
                selfie, folderId
        );
    }

    // Download a photo from Google Drive
    @GetMapping("/download/{fileId}")
    public ResponseEntity<byte[]> downloadPhoto(
            @PathVariable String fileId,
            @RequestParam(value = "name", defaultValue = "photo.jpg") String name) {

        try {
            byte[] data = googleDriveService.downloadFile(fileId);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + name + "\"")
                    .header(HttpHeaders.CONTENT_TYPE,
                            MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .body(data);

        } catch (Exception e) {
            System.out.println("Download error: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ============ ADMIN ENDPOINTS ============

    // Create a new event folder
    @PostMapping("/admin/create-folder")
    public ResponseEntity<Map<String, String>> createEventFolder(
            @RequestParam("name") String folderName) {

        try {
            File folder = googleDriveService.createFolder(folderName);

            Map<String, String> response = new HashMap<>();
            response.put("id", folder.getId());
            response.put("name", folder.getName());
            response.put("status", "success");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("Create folder error: " + e.getMessage());

            Map<String, String> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());

            return ResponseEntity.internalServerError().body(error);
        }
    }

    // Upload photos to an event folder
    @PostMapping("/admin/upload")
    public ResponseEntity<Map<String, Object>> uploadPhotos(
            @RequestParam("folderId") String folderId,
            @RequestParam("files") MultipartFile[] files) {

        try {
            int uploaded = 0;

            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    googleDriveService.uploadFileToDrive(
                            folderId,
                            file.getOriginalFilename(),
                            file.getBytes(),
                            file.getContentType()
                    );
                    uploaded++;
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("uploaded", uploaded);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("Upload error: " + e.getMessage());

            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());

            return ResponseEntity.internalServerError().body(error);
        }
    }

    // Delete a file from Google Drive
    @DeleteMapping("/admin/delete/{fileId}")
    public ResponseEntity<Map<String, String>> deletePhoto(
            @PathVariable String fileId) {

        try {
            googleDriveService.deleteFile(fileId);

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("Delete error: " + e.getMessage());

            Map<String, String> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());

            return ResponseEntity.internalServerError().body(error);
        }
    }
}