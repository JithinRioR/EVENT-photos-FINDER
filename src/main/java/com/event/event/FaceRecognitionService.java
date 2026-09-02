package com.event.event;

import org.opencv.core.Mat;
import org.opencv.objdetect.FaceRecognizerSF;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@Service
public class FaceRecognitionService {

    private FaceRecognizerSF faceRecognizer;

    @PostConstruct
    public void loadModels() {

        try {
            nu.pattern.OpenCV.loadLocally();

            ClassPathResource resource =
                    new ClassPathResource(
                            "models/face_recognition_sface_2021dec.onnx"
                    );

            File modelFile = File.createTempFile(
                    "face_recognition_sface_2021dec",
                    ".onnx"
            );

            modelFile.deleteOnExit();

            try (InputStream inputStream = resource.getInputStream()) {
                Files.copy(
                        inputStream,
                        modelFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            System.out.println(
                    "SFace model path: "
                            + modelFile.getAbsolutePath()
            );

            faceRecognizer = FaceRecognizerSF.create(
                    modelFile.getAbsolutePath(),
                    ""
            );

            System.out.println("SFace face recognition model loaded successfully!");

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to initialize SFace model",
                    e
            );
        }
    }

    // Create SFace feature from detected face
    public synchronized Mat getFeature(Mat image, Mat faceDetection) {

        Mat alignedFace = new Mat();

        faceRecognizer.alignCrop(
                image,
                faceDetection,
                alignedFace
        );

        Mat feature = new Mat();

        faceRecognizer.feature(
                alignedFace,
                feature
        );

        alignedFace.release();
        Mat result = feature.clone();
        feature.release();
        return result;
    }

    // Compare two SFace features
    public synchronized double compare(Mat feature1, Mat feature2) {

        return faceRecognizer.match(
                feature1,
                feature2,
                FaceRecognizerSF.FR_COSINE
        );
    }
}