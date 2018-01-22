package org.entur.asag.service;

import com.google.cloud.storage.Storage;
import org.apache.camel.Header;
import org.rutebanken.helper.gcp.BlobStoreHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;

import static org.entur.asag.mapbox.MapBoxUpdateRouteBuilder.FILE_HANDLE;

@Service
public class BlobStoreService {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Storage storage;
    private final String containerName;

    @Autowired
    public BlobStoreService(@Value("${blobstore.gcs.credential.path}") String credentialPath,
                                @Value("${blobstore.gcs.container.name}") String containerName,
                                @Value("${blobstore.gcs.project.id}") String projectId) {
        this.storage = BlobStoreHelper.getStorage(credentialPath, projectId);
        this.containerName = containerName;
        logger.info("Blobstore service set up. project: {}, container: {}", projectId, containerName);
    }

    public InputStream getBlob(@Header(value = FILE_HANDLE) String name) {
        logger.info("Getting blob: {} from container {}, storage {}", name, containerName, storage);
        return BlobStoreHelper.getBlob(storage, containerName, name);
    }
}
