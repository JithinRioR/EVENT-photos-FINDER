package com.event.event;

import com.google.api.services.drive.model.File;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/photos")
@CrossOrigin
public class PhotoMatchingController {

    private final PhotoMatchingService photoMatchingService;

    public PhotoMatchingController(
            PhotoMatchingService photoMatchingService) {

        this.photoMatchingService =
                photoMatchingService;
    }

    @PostMapping(
            value = "/find",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public List<File> findPhotos(
            @RequestParam("selfie") MultipartFile selfie)
            throws Exception {

        return photoMatchingService.findMatchingPhotos(
                selfie
        );
    }
}