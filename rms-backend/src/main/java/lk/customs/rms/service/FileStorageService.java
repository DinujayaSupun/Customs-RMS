package lk.customs.rms.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    // Saves file under upload-dir and returns relative file path (to store in DB)
    String saveDocumentAttachment(Long documentId, Integer versionNo, MultipartFile file);

    String saveProfilePicture(Long userId, MultipartFile file);

    void deleteIfExists(String relativePath);

    Resource loadAsResource(String relativePath);
}
