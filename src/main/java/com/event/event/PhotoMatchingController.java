package com.event.event;

import com.google.api.services.drive.model.File;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    // View/Stream a photo directly for inline display
    @GetMapping("/view/{fileId}")
    public ResponseEntity<byte[]> viewPhoto(@PathVariable String fileId) {
        try {
            byte[] data = googleDriveService.downloadFile(fileId);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(data);

        } catch (Exception e) {
            System.err.println("View photo error for " + fileId + ": " + e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    // Download multiple photos as a single ZIP archive
    @PostMapping("/download-zip")
    public ResponseEntity<byte[]> downloadZip(
            @RequestBody List<Map<String, String>> filesToZip,
            @RequestParam(value = "zipName", defaultValue = "event-photos.zip") String zipName) {

        if (filesToZip == null || filesToZip.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            Set<String> addedNames = new HashSet<>();
            for (Map<String, String> fileInfo : filesToZip) {
                String fileId = fileInfo.get("id");
                String fileName = fileInfo.getOrDefault("name", "photo.jpg");

                if (fileId == null || fileId.isBlank()) continue;

                // Ensure unique filenames inside zip
                String uniqueName = fileName;
                int counter = 1;
                while (addedNames.contains(uniqueName)) {
                    int dotIndex = fileName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        uniqueName = fileName.substring(0, dotIndex) + "_" + counter + fileName.substring(dotIndex);
                    } else {
                        uniqueName = fileName + "_" + counter;
                    }
                    counter++;
                }
                addedNames.add(uniqueName);

                try {
                    byte[] fileData = googleDriveService.downloadFile(fileId);
                    ZipEntry entry = new ZipEntry(uniqueName);
                    zos.putNextEntry(entry);
                    zos.write(fileData);
                    zos.closeEntry();
                } catch (Exception e) {
                    System.err.println("Could not add file to zip: " + fileName + " (" + e.getMessage() + ")");
                }
            }

            zos.finish();
            byte[] zipBytes = baos.toByteArray();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipName + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                    .body(zipBytes);

        } catch (Exception e) {
            System.err.println("Zip creation error: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ============ ADMIN ENDPOINTS ============

    // Sync files and extract faces into local DB cache
    @PostMapping("/admin/sync")
    public ResponseEntity<Map<String, Object>> syncEventPhotos(
            @RequestParam("folderId") String folderId) {

        try {
            Map<String, Object> syncResult = photoMatchingService.syncPhotos(folderId);
            syncResult.put("status", "success");
            return ResponseEntity.ok(syncResult);
        } catch (Exception e) {
            System.err.println("Sync error: " + e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

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