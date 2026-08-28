package com.event.event;

import com.google.api.services.drive.model.File;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
public class PhotoMatchingService {

    private final FaceDetector faceDetector;
    private final FaceRecognitionService faceRecognitionService;
    private final GoogleDriveService googleDriveService;

    public PhotoMatchingService(
            FaceDetector faceDetector,
            FaceRecognitionService faceRecognitionService,
            GoogleDriveService googleDriveService) {

        this.faceDetector = faceDetector;
        this.faceRecognitionService = faceRecognitionService;
        this.googleDriveService = googleDriveService;
    }

    public List<File> findMatchingPhotos(
            MultipartFile selfie, String folderId) throws Exception {

        List<File> matches = new ArrayList<>();

        // Convert selfie to OpenCV Mat
        MatOfByte selfieBytes =
                new MatOfByte(selfie.getBytes());

        Mat selfieImage =
                Imgcodecs.imdecode(
                        selfieBytes,
                        Imgcodecs.IMREAD_COLOR
                );

        if (selfieImage.empty()) {
            throw new RuntimeException("Could not read selfie!");
        }

        // Detect face in selfie
        Mat selfieFaces =
                faceDetector.detectFace(selfieImage);

        if (selfieFaces.empty() || selfieFaces.rows() == 0) {
            throw new RuntimeException(
                    "No face detected in selfie!"
            );
        }

        // First detected face = user's face
        Mat selfieFace =
                selfieFaces.row(0);

        Mat selfieFeature =
                faceRecognitionService.getFeature(
                        selfieImage,
                        selfieFace
                );

        // Get Google Drive files from chosen folder
        List<File> driveFiles =
                googleDriveService.getDriveFiles(folderId);

        for (File file : driveFiles) {

            // Only process images
            if (file.getMimeType() == null ||
                    !file.getMimeType().startsWith("image/")) {
                continue;
            }

            try {

                // Download Drive image
                byte[] imageBytes =
                        googleDriveService.downloadFile(
                                file.getId()
                        );

                MatOfByte bytes =
                        new MatOfByte(imageBytes);

                Mat image =
                        Imgcodecs.imdecode(
                                bytes,
                                Imgcodecs.IMREAD_COLOR
                        );

                if (image.empty()) {
                    continue;
                }

                // Detect faces
                Mat faces =
                        faceDetector.detectFace(image);

                if (faces.empty() || faces.rows() == 0) {
                    continue;
                }

                boolean matched = false;

                // Check every face in the photo
                for (int i = 0; i < faces.rows(); i++) {

                    Mat detectedFace =
                            faces.row(i);

                    Mat feature =
                            faceRecognitionService.getFeature(
                                    image,
                                    detectedFace
                            );

                    double score =
                            faceRecognitionService.compare(
                                    selfieFeature,
                                    feature
                            );

                    System.out.println(
                            file.getName()
                                    + " → similarity: "
                                    + score
                    );

                    // SFace cosine threshold
                    if (score >= 0.363) {
                        matched = true;
                        break;
                    }
                }

                if (matched) {
                    matches.add(file);
                }

            } catch (Exception e) {

                System.out.println(
                        "Could not process "
                                + file.getName()
                                + ": "
                                + e.getMessage()
                );
            }
        }

        return matches;
    }
}