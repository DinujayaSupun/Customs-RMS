package lk.customs.rms.service.impl;

import lk.customs.rms.exception.BadRequestException;
import lk.customs.rms.exception.ResourceNotFoundException;
import lk.customs.rms.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private final Path uploadRoot;

    public FileStorageServiceImpl(@Value("${app.upload-dir}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadRoot);
        } catch (IOException e) {
            throw new BadRequestException("Could not create upload directory: " + this.uploadRoot);
        }
    }

    @Override
    public String saveDocumentAttachment(Long documentId, Integer versionNo, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required.");
        }

        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());

        // Block path traversal like ../../
        if (originalName.contains("..")) {
            throw new BadRequestException("Invalid file name: " + originalName);
        }

        // Folder per document
        Path docFolder = uploadRoot.resolve("document-" + documentId).normalize();
        try {
            Files.createDirectories(docFolder);
        } catch (IOException e) {
            throw new BadRequestException("Could not create folder for document: " + documentId);
        }

        // Unique stored name to avoid collisions
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0 && dot < originalName.length() - 1) {
            ext = originalName.substring(dot); // includes dot
        }

        String storedName = "v" + versionNo + "_" + UUID.randomUUID() + ext;
        Path destination = docFolder.resolve(storedName).normalize();

        try {
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BadRequestException("Failed to store file: " + originalName);
        }

        // Return RELATIVE path (store in DB)
        return uploadRoot.relativize(destination).toString().replace("\\", "/");
    }

    @Override
    public Resource loadAsResource(String relativePath) {
        try {
            Path file = uploadRoot.resolve(relativePath).normalize();
            if (!file.startsWith(uploadRoot)) {
                throw new BadRequestException("Invalid file path.");
            }

            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new ResourceNotFoundException("File not found.");
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("File not found.");
        }
    }
}
