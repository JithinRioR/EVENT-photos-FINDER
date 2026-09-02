package com.event.event;

import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.objdetect.FaceDetectorYN;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@Service
public class FaceDetector {

    private final FaceDetectorYN detector;

    public FaceDetector() {

        try {

            // Load OpenCV native library automatically
            OpenCV.loadLocally();

            System.out.println(
                    "OpenCV loaded successfully!"
            );

            // Load YuNet model from resources
            ClassPathResource resource =
                    new ClassPathResource(
                            "models/face_detection_yunet_2023mar.onnx"
                    );

            File modelFile = File.createTempFile(
                    "face_detection_yunet_2023mar",
                    ".onnx"
            );

            modelFile.deleteOnExit();

            try (InputStream inputStream =
                         resource.getInputStream()) {

                Files.copy(
                        inputStream,
                        modelFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            System.out.println(
                    "YuNet model path: "
                            + modelFile.getAbsolutePath()
            );

            detector = FaceDetectorYN.create(
                    modelFile.getAbsolutePath(),
                    "",
                    new Size(320, 320),
                    0.55f, // Score threshold (high sensitivity for group & angled faces)
                    0.3f,  // NMS threshold
                    5000   // Top K faces
            );

            System.out.println(
                    "YuNet face detector loaded successfully!"
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to initialize OpenCV / YuNet",
                    e
            );
        }
    }

    public synchronized Mat detectFace(Mat image) {

        if (image == null || image.empty()) {
            return new Mat();
        }

        detector.setInputSize(
                new Size(image.cols(), image.rows())
        );

        Mat faces = new Mat();

        detector.detect(image, faces);

        return faces;
    }
}