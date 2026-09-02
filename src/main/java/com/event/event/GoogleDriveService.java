package com.event.event;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@Service
public class GoogleDriveService {

    private static final String CREDENTIALS_FILE =
            "/credentials/metal-dimension-506709-e5-dafe3e9c0a4a.json";

    // The parent folder that contains all event folders
    private static final String PARENT_FOLDER_ID =
            "1ETba7ZKTXc_M69X22UZV65EArYczahoW";

    public Drive getDriveService() throws Exception {

        InputStream credentialsStream = null;

        // Check environment variables first (for Render / cloud)
        String envCredentials = System.getenv("GOOGLE_CREDENTIALS_JSON");
        if (envCredentials == null || envCredentials.trim().isEmpty()) {
            envCredentials = System.getenv("GOOGLE_DRIVE_CREDENTIALS");
        }
        if (envCredentials == null || envCredentials.trim().isEmpty()) {
            envCredentials = System.getenv("GOOGLE_CREDENTIALS");
        }

        if (envCredentials != null && !envCredentials.trim().isEmpty()) {
            credentialsStream = new java.io.ByteArrayInputStream(
                    envCredentials.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
        } else {
            // Fallback to ClassPathResource inside JAR / resources
            try {
                org.springframework.core.io.ClassPathResource resource =
                        new org.springframework.core.io.ClassPathResource(
                                "credentials/metal-dimension-506709-e5-dafe3e9c0a4a.json"
                        );
                if (resource.exists()) {
                    credentialsStream = resource.getInputStream();
                }
            } catch (Exception e) {
                System.err.println("Could not load credentials from ClassPathResource: " + e.getMessage());
            }

            if (credentialsStream == null) {
                credentialsStream = getClass().getResourceAsStream(CREDENTIALS_FILE);
            }
        }

        if (credentialsStream == null) {
            throw new RuntimeException(
                    "Google Drive credentials not found! Please set GOOGLE_CREDENTIALS_JSON environment variable in Render."
            );
        }

        // Full DRIVE scope to allow creating folders, uploading, deleting
        GoogleCredentials credentials =
                GoogleCredentials
                        .fromStream(credentialsStream)
                        .createScoped(
                                Collections.singleton(
                                        DriveScopes.DRIVE
                                )
                        );

        return new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        )
        .setApplicationName("Event Photo Finder")
        .build();
    }

    // List all event folders (subfolders inside the parent folder)
    public List<File> getFolders() throws Exception {

        Drive drive = getDriveService();

        String query = "'" + PARENT_FOLDER_ID + "' in parents"
                + " and mimeType = 'application/vnd.google-apps.folder'"
                + " and trashed = false";

        FileList result = drive.files().list()
                .setQ(query)
                .setPageSize(50)
                .setFields("files(id,name)")
                .execute();

        return result.getFiles();
    }

    // Get image files from a specific folder
    public List<File> getDriveFiles(String folderId) throws Exception {

        Drive drive = getDriveService();

        String query = "'" + folderId + "' in parents and trashed = false";

        FileList result = drive.files().list()
                .setQ(query)
                .setPageSize(100)
                .setFields("files(id,name,mimeType,webViewLink,thumbnailLink)")
                .execute();

        return result.getFiles();
    }

    // Download a photo from Google Drive
    public byte[] downloadFile(String fileId) throws Exception {

        Drive drive = getDriveService();

        ByteArrayOutputStream outputStream =
                new ByteArrayOutputStream();

        drive.files()
                .get(fileId)
                .executeMediaAndDownloadTo(outputStream);

        return outputStream.toByteArray();
    }

    // Create a new event folder inside the parent folder
    public File createFolder(String folderName) throws Exception {

        Drive drive = getDriveService();

        File folderMetadata = new File();
        folderMetadata.setName(folderName);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        folderMetadata.setParents(
                Collections.singletonList(PARENT_FOLDER_ID)
        );

        return drive.files().create(folderMetadata)
                .setFields("id,name")
                .execute();
    }

    // Upload a file to a specific event folder
    public File uploadFileToDrive(
            String folderId,
            String fileName,
            byte[] content,
            String mimeType) throws Exception {

        Drive drive = getDriveService();

        File fileMetadata = new File();
        fileMetadata.setName(fileName);
        fileMetadata.setParents(
                Collections.singletonList(folderId)
        );

        ByteArrayContent mediaContent =
                new ByteArrayContent(mimeType, content);

        return drive.files().create(fileMetadata, mediaContent)
                .setFields("id,name,mimeType,webViewLink")
                .execute();
    }

    // Delete a file from Google Drive
    public void deleteFile(String fileId) throws Exception {

        Drive drive = getDriveService();
        drive.files().delete(fileId).execute();
    }
}