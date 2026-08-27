package com.event.event;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.objdetect.FaceDetectorYN;
import org.springframework.stereotype.Service;

@Service
public class FaceDetector {

    private final FaceDetectorYN detector;

    public FaceDetector() {

        // Load OpenCV
        nu.pattern.OpenCV.loadLocally();

        System.out.println("OpenCV loaded successfully: "
                + org.opencv.core.Core.VERSION);

        // YuNet model
        String modelPath =
                "src/main/resources/models/face_detection_yunet_2023mar.onnx";

        // Create YuNet detector
        detector = FaceDetectorYN.create(
                modelPath,
                "",
                new Size(320, 320)
        );

        System.out.println("YuNet face detector loaded successfully!");
    }

    public Mat detectFace(Mat image) {

        if (image == null || image.empty()) {
            return new Mat();
        }

        // Set input size according to uploaded image
        detector.setInputSize(
                new Size(image.cols(), image.rows())
        );

        // Detect faces
        Mat faces = new Mat();

        detector.detect(image, faces);

        return faces;
    }
}