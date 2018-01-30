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

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import org.apache.camel.Body;
import org.apache.camel.Header;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.entur.asag.mapbox.model.MapBoxAwsCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

import static org.apache.camel.Exchange.FILE_NAME;


/**
 * Implemented because of the requirement to use aws client with temporary session token.
 */
@Service
public class AwsS3Uploader {
    private static final Logger logger = LoggerFactory.getLogger(AwsS3Uploader.class);

    public AmazonS3Client createClient(MapBoxAwsCredentials mapBoxAwsCredentials) {
        AWSCredentials credentials = new BasicSessionCredentials(mapBoxAwsCredentials.getAccessKeyId(),
                mapBoxAwsCredentials.getSecretAccessKey(), mapBoxAwsCredentials.getSessionToken());

        return new AmazonS3Client(credentials);
    }

    public void upload(@Header("credentials") MapBoxAwsCredentials credentials,
                       @Header(FILE_NAME) String filename,
                       @Body InputStream inputStream) throws IOException {
        logger.info("Uploading inputStream {} to aws. bucket: {}, key: {}, filename: {}", inputStream, credentials.getBucket(), credentials.getKey(), filename);
        AmazonS3Client amazonS3Client = createClient(credentials);
        amazonS3Client.setRegion(Region.getRegion(Regions.US_EAST_1));

        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType("application/json");
        objectMetadata.setContentLength(inputStream.available());

        logger.info(ToStringBuilder.reflectionToString(objectMetadata));
        amazonS3Client.putObject(credentials.getBucket(), credentials.getKey(), inputStream, objectMetadata);
    }
}
