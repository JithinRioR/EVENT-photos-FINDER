package com.event.event;

import com.event.event.entity.DrivePhotoFace;
import com.event.event.repository.DrivePhotoFaceRepository;
import com.google.api.services.drive.model.File;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class PhotoMatchingService {

    private final FaceDetector faceDetector;
    private final FaceRecognitionService faceRecognitionService;
    private final GoogleDriveService googleDriveService;
    private final DrivePhotoFaceRepository drivePhotoFaceRepository;

    private final Map<String, Map<String, Object>> syncProgressMap = new ConcurrentHashMap<>();
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();

    public PhotoMatchingService(
            FaceDetector faceDetector,
            FaceRecognitionService faceRecognitionService,
            GoogleDriveService googleDriveService,
            DrivePhotoFaceRepository drivePhotoFaceRepository) {

        this.faceDetector = faceDetector;
        this.faceRecognitionService = faceRecognitionService;
        this.googleDriveService = googleDriveService;
        this.drivePhotoFaceRepository = drivePhotoFaceRepository;
    }

    private Mat resizeImageIfNeeded(Mat image) {
        int maxDim = 800;
        if (image.cols() > maxDim || image.rows() > maxDim) {
            double scale = (double) maxDim / Math.max(image.cols(), image.rows());
            Mat resized = new Mat();
            Imgproc.resize(image, resized, new Size((int)(image.cols() * scale), (int)(image.rows() * scale)));
            image.release(); // Free original high-res image memory immediately
            return resized;
        }
        return image;
    }

    private String serializeEmbedding(Mat feature) {
        float[] floatArray = new float[(int) (feature.total())];
        feature.get(0, 0, floatArray);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < floatArray.length; i++) {
            sb.append(floatArray[i]);
            if (i < floatArray.length - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    private Mat deserializeEmbedding(String serialized) {
        String[] tokens = serialized.split(",");
        float[] floatArray = new float[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            floatArray[i] = Float.parseFloat(tokens[i]);
        }
        Mat feature = new Mat(1, tokens.length, CvType.CV_32FC1);
        feature.put(0, 0, floatArray);
        return feature;
    }

    // ==========================================
    // INSTANT DATABASE SEARCH (In-Memory Math)
    // ==========================================
    public List<File> findMatchingPhotos(
            MultipartFile selfie, String folderId) throws Exception {

        List<File> matches = new ArrayList<>();
        Set<String> matchedFileIds = new HashSet<>(); // Avoid duplicate photos for multiple matching faces

        // Convert selfie to OpenCV Mat
        MatOfByte selfieBytes = new MatOfByte(selfie.getBytes());
        Mat selfieImage = Imgcodecs.imdecode(selfieBytes, Imgcodecs.IMREAD_COLOR);
        selfieBytes.release(); // Free immediately

        if (selfieImage.empty()) {
            throw new RuntimeException("Could not read selfie!");
        }

        // Resize selfie if needed
        selfieImage = resizeImageIfNeeded(selfieImage);

        // Detect face in selfie
        Mat selfieFaces = faceDetector.detectFace(selfieImage);

        if (selfieFaces.empty() || selfieFaces.rows() == 0) {
            selfieImage.release();
            selfieFaces.release();
            throw new RuntimeException("No face detected in selfie!");
        }

        // Extract first face embedding
        Mat selfieFace = selfieFaces.row(0);
        Mat selfieFeature = faceRecognitionService.getFeature(selfieImage, selfieFace);

        // Get pre-saved face embeddings from DB (instead of downloading files from Drive)
        List<DrivePhotoFace> dbFaces = drivePhotoFaceRepository.findByFolderId(folderId);
        System.out.println("Comparing selfie with " + dbFaces.size() + " pre-cached faces in DB...");

        for (DrivePhotoFace dbFace : dbFaces) {
            if (matchedFileIds.contains(dbFace.getFileId())) {
                continue; // Already matched this photo
            }

            Mat dbFeatureMat = null;
            try {
                dbFeatureMat = deserializeEmbedding(dbFace.getEmbedding());
                double score = faceRecognitionService.compare(selfieFeature, dbFeatureMat);

                // Cosine similarity threshold for SFace model
                if (score >= 0.363) {
                    matchedFileIds.add(dbFace.getFileId());

                    // Create file stub to return to client
                    File fileStub = new File();
                    fileStub.setId(dbFace.getFileId());
                    fileStub.setName(dbFace.getFileName());
                    fileStub.setWebViewLink(dbFace.getWebViewLink());
                    fileStub.setThumbnailLink("https://lh3.googleusercontent.com/d/" + dbFace.getFileId() + "=s400");
                    matches.add(fileStub);
                }
            } catch (Exception e) {
                System.err.println("Error comparing face ID " + dbFace.getId() + ": " + e.getMessage());
            } finally {
                if (dbFeatureMat != null) {
                    dbFeatureMat.release();
                }
            }
        }

        selfieImage.release();
        selfieFaces.release();
        selfieFeature.release();
        return matches;
    }

    // ==========================================
    // ASYNC BACKGROUND SYNC & CACHE FACE FEATURES
    // ==========================================
    public Map<String, Object> getSyncStatus(String folderId) {
        return syncProgressMap.getOrDefault(folderId, Collections.singletonMap("status", "idle"));
    }

    public synchronized Map<String, Object> startSync(String folderId) {
        Map<String, Object> current = syncProgressMap.get(folderId);
        if (current != null && "running".equals(current.get("status"))) {
            return current;
        }

        Map<String, Object> progress = new ConcurrentHashMap<>();
        progress.put("status", "running");
        progress.put("processed", 0);
        progress.put("total", 0);
        progress.put("newlySynced", 0);
        progress.put("alreadySynced", 0);
        progress.put("failed", 0);
        progress.put("message", "Connecting to Google Drive...");
        syncProgressMap.put(folderId, progress);

        syncExecutor.submit(() -> {
            try {
                processSyncInternal(folderId, progress);
            } catch (Exception e) {
                progress.put("status", "error");
                progress.put("message", e.getMessage() != null ? e.getMessage() : "Sync failed");
                System.err.println("Async sync error for folder " + folderId + ": " + e.getMessage());
            }
        });

        return progress;
    }

    private void processSyncInternal(String folderId, Map<String, Object> progress) throws Exception {
        // Fetch all current files in the Google Drive parent folder
        List<File> driveFiles = googleDriveService.getDriveFiles(folderId);
        List<File> imageFiles = new ArrayList<>();
        Set<String> currentFileIds = new HashSet<>();

        for (File f : driveFiles) {
            if (f.getMimeType() != null && f.getMimeType().startsWith("image/")) {
                imageFiles.add(f);
                currentFileIds.add(f.getId());
            }
        }

        int totalImages = imageFiles.size();
        progress.put("total", totalImages);

        int newlySynced = 0;
        int alreadySynced = 0;
        int failed = 0;
        int processed = 0;

        for (File file : imageFiles) {
            processed++;
            progress.put("processed", processed);

            // If already indexed in DB, skip download
            if (drivePhotoFaceRepository.existsByFileId(file.getId())) {
                alreadySynced++;
                progress.put("alreadySynced", alreadySynced);
                continue;
            }

            Mat image = null;
            MatOfByte bytes = null;
            Mat faces = null;
            try {
                // Download file bytes
                byte[] imageBytes = googleDriveService.downloadFile(file.getId());
                bytes = new MatOfByte(imageBytes);
                image = Imgcodecs.imdecode(bytes, Imgcodecs.IMREAD_COLOR);
                
                bytes.release(); // Free immediately after decoding
                bytes = null;

                if (image.empty()) {
                    failed++;
                    progress.put("failed", failed);
                    continue;
                }

                // Resize image to max 800px dimension
                image = resizeImageIfNeeded(image);

                // Run face detector
                faces = faceDetector.detectFace(image);

                if (faces.empty() || faces.rows() == 0) {
                    // Save photo even if no faces found (with empty embedding) so we don't re-download next time
                    DrivePhotoFace emptyFace = new DrivePhotoFace(
                            file.getId(),
                            file.getName(),
                            folderId,
                            file.getWebViewLink(),
                            ""
                    );
                    drivePhotoFaceRepository.save(emptyFace);
                    newlySynced++;
                    progress.put("newlySynced", newlySynced);
                    continue;
                }

                // Save each detected face in this photo
                for (int i = 0; i < faces.rows(); i++) {
                    Mat detectedFace = faces.row(i);
                    Mat feature = faceRecognitionService.getFeature(image, detectedFace);

                    if (feature.empty()) {
                        feature.release();
                        continue;
                    }

                    String serializedFeature = serializeEmbedding(feature);

                    DrivePhotoFace faceRecord = new DrivePhotoFace(
                            file.getId(),
                            file.getName(),
                            folderId,
                            file.getWebViewLink(),
                            serializedFeature
                    );

                    drivePhotoFaceRepository.save(faceRecord);
                    feature.release(); // Free embedding Mat
                }

                newlySynced++;
                progress.put("newlySynced", newlySynced);

            } catch (Exception e) {
                failed++;
                progress.put("failed", failed);
                System.err.println("Could not sync " + file.getName() + ": " + e.getMessage());
            } finally {
                if (bytes != null) {
                    bytes.release();
                }
                if (faces != null) {
                    faces.release();
                }
                if (image != null && !image.empty()) {
                    image.release();
                }
            }
        }

        // Clean up database records for files that were deleted from Google Drive
        List<DrivePhotoFace> cachedFolderFaces = drivePhotoFaceRepository.findByFolderId(folderId);
        int deletedFromCache = 0;
        for (DrivePhotoFace cachedFace : cachedFolderFaces) {
            if (!currentFileIds.contains(cachedFace.getFileId())) {
                drivePhotoFaceRepository.deleteByFileId(cachedFace.getFileId());
                deletedFromCache++;
            }
        }

        progress.put("deletedFromCache", deletedFromCache);
        progress.put("status", "completed");
        progress.put("message", "Sync completed successfully.");
    }
}
