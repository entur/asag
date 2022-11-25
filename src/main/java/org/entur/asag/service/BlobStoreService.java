/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package org.entur.asag.service;

import com.google.cloud.storage.Storage;
import org.apache.camel.Header;
import org.rutebanken.helper.gcp.BlobStoreHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.InputStream;

import static org.entur.asag.mapbox.MapBoxUpdateRouteBuilder.FILE_HANDLE;

@Profile("!test")
@Service
public class BlobStoreService {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Storage storage;
    private final String containerName;

    @Autowired
    public BlobStoreService(@Value("${blobstore.gcs.credential.path:#{null}}") String credentialPath,
                                @Value("${blobstore.gcs.container.name}") String containerName,
                                @Value("${blobstore.gcs.project.id}") String projectId) {
        if (credentialPath == null || credentialPath.isEmpty()) {
            // Used default credentials
            this.storage = BlobStoreHelper.getStorage(projectId);
        } else {
            this.storage = BlobStoreHelper.getStorage(credentialPath, projectId);
        }
        this.containerName = containerName;
        logger.info("Blobstore service set up. project: {}, container: {}", projectId, containerName);
    }

    public InputStream getBlob(@Header(value = FILE_HANDLE) String name) {
        logger.info("Getting blob: {} from container {}", name, containerName);
        return BlobStoreHelper.getBlob(storage, containerName, name);
    }
}
