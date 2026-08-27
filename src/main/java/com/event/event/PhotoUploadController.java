package com.event.event;

import com.event.event.entity.Photo;
import com.event.event.repository.PhotoRepository;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class PhotoUploadController {

    private final PhotoRepository photoRepository;

    public PhotoUploadController(PhotoRepository photoRepository) {
        this.photoRepository = photoRepository;
    }

    @PostMapping("/upload-photo")
    public String uploadPhoto(
            @RequestParam("photo") MultipartFile photo,
            @RequestParam("event") String event,
            @RequestParam("name") String name) {

        try {

            Path uploadPath = Paths.get("uploads");

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalName = photo.getOriginalFilename();

            if (originalName == null || originalName.isBlank()) {
                originalName = "photo.jpg";
            }

            String fileName =
                    System.currentTimeMillis() + "_" + originalName;

            Path filePath = uploadPath.resolve(fileName);

            Files.copy(photo.getInputStream(), filePath);

            Photo photoData = new Photo(
                    fileName,
                    "/photos/" + fileName,
                    event
            );

            photoRepository.save(photoData);

            System.out.println("Student: " + name);
            System.out.println("Event: " + event);
            System.out.println("Photo: " + fileName);

            return "Photo uploaded successfully! 📸";

        } catch (IOException e) {

            e.printStackTrace();

            return "Photo upload failed! ❌";
        }
    }
}