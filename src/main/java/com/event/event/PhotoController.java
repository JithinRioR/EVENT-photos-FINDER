package com.event.event;

import com.event.event.entity.Photo;
import com.event.event.repository.PhotoRepository;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/photos")
@CrossOrigin
public class PhotoController {

    private final PhotoRepository photoRepository;
    private final FaceDetector faceDetector;

    public PhotoController(
            PhotoRepository photoRepository,
            FaceDetector faceDetector) {

        this.photoRepository = photoRepository;
        this.faceDetector = faceDetector;
    }

    // Save photo information
    @PostMapping
    public Photo addPhoto(@RequestBody Photo photo) {
        return photoRepository.save(photo);
    }

    // Get all photos
    @GetMapping
    public List<Photo> getAllPhotos() {
        return photoRepository.findAll();
    }

    // Receive user's selfie
    @PostMapping("/upload")
    public ResponseEntity<String> uploadPhoto(
            @RequestParam("photo") MultipartFile photo,
            @RequestParam("event") String event,
            @RequestParam("name") String name) {

        try {

            File tempFile = File.createTempFile(
                    "selfie-",
                    ".jpg"
            );

            photo.transferTo(tempFile);

            // Read uploaded selfie
            Mat image = Imgcodecs.imread(
                    tempFile.getAbsolutePath()
            );

            if (image.empty()) {
                tempFile.delete();

                return ResponseEntity.badRequest()
                        .body("Could not read the uploaded photo.");
            }

            // Detect face
            Mat faces = faceDetector.detectFace(image);

            int numberOfFaces = faces.rows();

            tempFile.delete();

            if (numberOfFaces == 0) {

                return ResponseEntity.ok(
                        "No face detected. Please upload a clear selfie."
                );
            }

            return ResponseEntity.ok(
                    "Face detected successfully! "
                    + "Now we can search your event photos."
            );

        } catch (IOException e) {

            e.printStackTrace();

            return ResponseEntity.internalServerError()
                    .body("Unable to process your photo.");
        }
    }
}