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

package org.entur.asag.mapbox;

import org.apache.camel.Body;
import org.apache.camel.Header;
import org.entur.asag.mapbox.model.MapBoxAwsCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;

import static org.apache.camel.Exchange.FILE_NAME;


/**
 * Implemented because of the requirement to use aws client with temporary session token.
 */
@Service
public class AwsS3Uploader {
    private static final Logger logger = LoggerFactory.getLogger(AwsS3Uploader.class);

    public S3Client createClient(MapBoxAwsCredentials creds) {
        AwsSessionCredentials sessionCreds = AwsSessionCredentials.create(
                creds.getAccessKeyId(), creds.getSecretAccessKey(), creds.getSessionToken());
        return S3Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(sessionCreds))
                .build();
    }

    public void upload(@Header("credentials") MapBoxAwsCredentials credentials,
                       @Header(FILE_NAME) String filename,
                       @Body InputStream inputStream) throws IOException {
        logger.info("Uploading inputStream {} to aws. bucket: {}, key: {}, filename: {}", inputStream, credentials.getBucket(), credentials.getKey(), filename);
        byte[] bytes = inputStream.readAllBytes();
        S3Client s3Client = createClient(credentials);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(credentials.getBucket())
                .key(credentials.getKey())
                .contentType("application/json")
                .contentLength((long) bytes.length)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(bytes));
    }
}
