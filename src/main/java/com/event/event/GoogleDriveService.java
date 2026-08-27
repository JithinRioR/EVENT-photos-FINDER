package com.event.event;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
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

    public Drive getDriveService() throws Exception {

        InputStream credentialsStream =
                getClass().getResourceAsStream(CREDENTIALS_FILE);

        if (credentialsStream == null) {
            throw new RuntimeException(
                    "Google credentials file not found: "
                    + CREDENTIALS_FILE
            );
        }

        GoogleCredentials credentials =
                GoogleCredentials
                        .fromStream(credentialsStream)
                        .createScoped(
                                Collections.singleton(
                                        DriveScopes.DRIVE_READONLY
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

    // Get files from Google Drive
    public List<File> getDriveFiles() throws Exception {

        Drive drive = getDriveService();

        FileList result = drive.files().list()
                .setPageSize(100)
                .setFields("files(id,name,mimeType,webViewLink)")
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
}