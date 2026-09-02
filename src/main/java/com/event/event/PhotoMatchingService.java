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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PhotoMatchingService {

    private final FaceDetector faceDetector;
    private final FaceRecognitionService faceRecognitionService;
    private final GoogleDriveService googleDriveService;
    private final DrivePhotoFaceRepository drivePhotoFaceRepository;

    private final Map<String, Map<String, Object>> syncProgressMap = new ConcurrentHashMap<>();
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService workerPool = Executors.newFixedThreadPool(3);

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
        int maxDim = 640;
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

    private boolean isSupportedImage(File f) {
        if (f == null || f.getName() == null) return false;
        String name = f.getName().toLowerCase();
        // Skip heavy camera RAW formats (.cr2, .nef, .arw, .dng, .raw) that OpenCV cannot decode
        if (name.endsWith(".cr2") || name.endsWith(".nef") || name.endsWith(".arw")
                || name.endsWith(".dng") || name.endsWith(".raw") || name.endsWith(".cr3")) {
            return false;
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".bmp")) {
            return true;
        }
        return f.getMimeType() != null && f.getMimeType().startsWith("image/") && !f.getMimeType().contains("raw");
    }

    // ==========================================
    // INSTANT DATABASE & ON-DEMAND FACE SEARCH
    // ==========================================
    public List<File> findMatchingPhotos(
            MultipartFile selfie, String folderId) throws Exception {

        List<File> matches = new ArrayList<>();
        Set<String> matchedFileIds = new HashSet<>();

        // Convert selfie to OpenCV Mat
        MatOfByte selfieBytes = new MatOfByte(selfie.getBytes());
        Mat selfieImage = Imgcodecs.imdecode(selfieBytes, Imgcodecs.IMREAD_COLOR);
        selfieBytes.release();

        if (selfieImage.empty()) {
            throw new RuntimeException("Could not read selfie image!");
        }

        selfieImage = resizeImageIfNeeded(selfieImage);
        Mat selfieFaces = faceDetector.detectFace(selfieImage);

        if (selfieFaces.empty() || selfieFaces.rows() == 0) {
            selfieImage.release();
            selfieFaces.release();
            throw new RuntimeException("No face detected in selfie! Please use a clear photo showing your face.");
        }

        // Extract face embeddings from the selfie
        List<Mat> selfieFeatures = new ArrayList<>();
        for (int i = 0; i < selfieFaces.rows(); i++) {
            Mat face = selfieFaces.row(i);
            Mat feat = faceRecognitionService.getFeature(selfieImage, face);
            if (!feat.empty()) {
                selfieFeatures.add(feat);
            }
        }

        selfieFaces.release();
        selfieImage.release();

        if (selfieFeatures.isEmpty()) {
            throw new RuntimeException("Could not extract facial features from selfie.");
        }

        // 1. Check existing cached faces in DB
        List<DrivePhotoFace> dbFaces = drivePhotoFaceRepository.findByFolderId(folderId);

        for (DrivePhotoFace dbFace : dbFaces) {
            if (matchedFileIds.contains(dbFace.getFileId())) {
                continue;
            }

            if (dbFace.getEmbedding() == null || dbFace.getEmbedding().isBlank()) {
                continue;
            }

            Mat dbFeatureMat = null;
            try {
                dbFeatureMat = deserializeEmbedding(dbFace.getEmbedding());

                for (Mat selfieFeature : selfieFeatures) {
                    double score = faceRecognitionService.compare(selfieFeature, dbFeatureMat);

                    // SFace cosine similarity threshold (0.34 is optimal for real-world event photos)
                    if (score >= 0.34) {
                        matchedFileIds.add(dbFace.getFileId());

                        File fileStub = new File();
                        fileStub.setId(dbFace.getFileId());
                        fileStub.setName(dbFace.getFileName());
                        fileStub.setWebViewLink(dbFace.getWebViewLink());
                        fileStub.setThumbnailLink("https://lh3.googleusercontent.com/d/" + dbFace.getFileId() + "=s400");
                        matches.add(fileStub);
                        break;
                    }
                }
            } catch (Exception e) {
                System.err.println("Error comparing face ID " + dbFace.getId() + ": " + e.getMessage());
            } finally {
                if (dbFeatureMat != null) {
                    dbFeatureMat.release();
                }
            }
        }

        // 2. Check if there are any unindexed image files in Google Drive
        List<File> driveFiles = googleDriveService.getDriveFiles(folderId);
        List<File> unsyncedFiles = new ArrayList<>();
        for (File f : driveFiles) {
            if (isSupportedImage(f) && !drivePhotoFaceRepository.existsByFileId(f.getId())) {
                unsyncedFiles.add(f);
            }
        }

        // Process any unindexed photos directly
        if (!unsyncedFiles.isEmpty()) {
            System.out.println("Processing " + unsyncedFiles.size() + " unindexed photos on the fly for folder " + folderId + "...");
            for (File file : unsyncedFiles) {
                try {
                    byte[] imageBytes = googleDriveService.downloadFile(file.getId());
                    MatOfByte bytes = new MatOfByte(imageBytes);
                    Mat image = Imgcodecs.imdecode(bytes, Imgcodecs.IMREAD_COLOR);
                    bytes.release();

                    if (image.empty()) continue;

                    image = resizeImageIfNeeded(image);
                    Mat faces = faceDetector.detectFace(image);

                    if (faces.empty() || faces.rows() == 0) {
                        DrivePhotoFace emptyFace = new DrivePhotoFace(file.getId(), file.getName(), folderId, file.getWebViewLink(), "");
                        drivePhotoFaceRepository.save(emptyFace);
                    } else {
                        boolean fileMatched = false;
                        for (int i = 0; i < faces.rows(); i++) {
                            Mat detectedFace = faces.row(i);
                            Mat feature = faceRecognitionService.getFeature(image, detectedFace);
                            if (feature.empty()) continue;

                            String serializedFeature = serializeEmbedding(feature);
                            DrivePhotoFace faceRecord = new DrivePhotoFace(file.getId(), file.getName(), folderId, file.getWebViewLink(), serializedFeature);
                            drivePhotoFaceRepository.save(faceRecord);

                            if (!fileMatched && !matchedFileIds.contains(file.getId())) {
                                for (Mat selfieFeature : selfieFeatures) {
                                    double score = faceRecognitionService.compare(selfieFeature, feature);
                                    if (score >= 0.34) {
                                        matchedFileIds.add(file.getId());
                                        File fileStub = new File();
                                        fileStub.setId(file.getId());
                                        fileStub.setName(file.getName());
                                        fileStub.setWebViewLink(file.getWebViewLink());
                                        fileStub.setThumbnailLink("https://lh3.googleusercontent.com/d/" + file.getId() + "=s400");
                                        matches.add(fileStub);
                                        fileMatched = true;
                                        break;
                                    }
                                }
                            }
                            feature.release();
                        }
                    }

                    faces.release();
                    image.release();

                } catch (Exception e) {
                    System.err.println("Could not process " + file.getName() + " on the fly: " + e.getMessage());
                }
            }
        }

        for (Mat sf : selfieFeatures) {
            sf.release();
        }

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

        AtomicInteger newlySynced = new AtomicInteger(0);
        AtomicInteger alreadySynced = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger processed = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (File file : imageFiles) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // If already indexed in DB, skip download
                    if (drivePhotoFaceRepository.existsByFileId(file.getId())) {
                        alreadySynced.incrementAndGet();
                        return;
                    }

                    byte[] imageBytes = googleDriveService.downloadFile(file.getId());
                    MatOfByte bytes = new MatOfByte(imageBytes);
                    Mat image = Imgcodecs.imdecode(bytes, Imgcodecs.IMREAD_COLOR);
                    bytes.release();

                    if (image.empty()) {
                        failed.incrementAndGet();
                        return;
                    }

                    // Resize image to max 640px dimension
                    image = resizeImageIfNeeded(image);

                    // Run face detector
                    Mat faces = faceDetector.detectFace(image);

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
                        newlySynced.incrementAndGet();
                    } else {
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
                            feature.release();
                        }
                        newlySynced.incrementAndGet();
                    }

                    faces.release();
                    image.release();

                } catch (Exception e) {
                    failed.incrementAndGet();
                    System.err.println("Could not sync " + file.getName() + ": " + e.getMessage());
                } finally {
                    int p = processed.incrementAndGet();
                    progress.put("processed", p);
                    progress.put("newlySynced", newlySynced.get());
                    progress.put("alreadySynced", alreadySynced.get());
                    progress.put("failed", failed.get());
                }
            }, workerPool);

            futures.add(future);
        }

        // Wait for all parallel photo indexing jobs to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

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
