package com.event.event.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "drive_photo_face")
public class DrivePhotoFace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileId;       // Google Drive file ID
    private String fileName;     // Google Drive file name
    private String folderId;     // Google Drive parent folder ID

    @Column(length = 2083)
    private String webViewLink;  // Link to open photo on Drive

    @Column(columnDefinition = "TEXT")
    private String embedding;    // 128-float face feature array serialized as comma-separated text

    public DrivePhotoFace() {
    }

    public DrivePhotoFace(String fileId, String fileName, String folderId, String webViewLink, String embedding) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.folderId = folderId;
        this.webViewLink = webViewLink;
        this.embedding = embedding;
    }

    public Long getId() {
        return id;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFolderId() {
        return folderId;
    }

    public void setFolderId(String folderId) {
        this.folderId = folderId;
    }

    public String getWebViewLink() {
        return webViewLink;
    }

    public void setWebViewLink(String webViewLink) {
        this.webViewLink = webViewLink;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }
}
