package com.event.event;

import org.opencv.core.Mat;
import org.opencv.objdetect.FaceRecognizerSF;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class FaceRecognitionService {

    private FaceRecognizerSF faceRecognizer;

    @PostConstruct
    public void loadModels() {

        nu.pattern.OpenCV.loadLocally();

        String modelPath =
                "src/main/resources/models/face_recognition_sface_2021dec.onnx";

        faceRecognizer = FaceRecognizerSF.create(
                modelPath,
                ""
        );

        System.out.println("SFace face recognition model loaded successfully!");
    }

    // Create SFace feature from detected face
    public Mat getFeature(Mat image, Mat faceDetection) {

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

        return feature;
    }

    // Compare two SFace features
    public double compare(Mat feature1, Mat feature2) {

        return faceRecognizer.match(
                feature1,
                feature2,
                FaceRecognizerSF.FR_COSINE
        );
    }
}