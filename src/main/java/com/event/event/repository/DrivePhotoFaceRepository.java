package com.event.event.repository;

import com.event.event.entity.DrivePhotoFace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DrivePhotoFaceRepository extends JpaRepository<DrivePhotoFace, Long> {

    List<DrivePhotoFace> findByFolderId(String folderId);

    boolean existsByFileId(String fileId);

    @Transactional
    void deleteByFileId(String fileId);

    @Transactional
    void deleteByFolderId(String folderId);
}
