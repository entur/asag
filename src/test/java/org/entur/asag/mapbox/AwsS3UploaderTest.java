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

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import org.entur.asag.mapbox.model.MapBoxAwsCredentials;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class AwsS3UploaderTest {

    @Test
    public void uploadCallsPutObjectWithCredentialBucketAndKey() throws IOException {
        AwsS3Uploader uploader = spy(new AwsS3Uploader());
        AmazonS3Client mockS3Client = mock(AmazonS3Client.class);
        doReturn(mockS3Client).when(uploader).createClient(any());

        MapBoxAwsCredentials credentials = credentials("my-bucket", "my/key");
        InputStream data = new ByteArrayInputStream("{\"type\":\"FeatureCollection\"}".getBytes(StandardCharsets.UTF_8));

        uploader.upload(credentials, "entur.geojson", data);

        verify(mockS3Client).putObject(eq("my-bucket"), eq("my/key"), any(InputStream.class), any(ObjectMetadata.class));
    }

    @Test
    public void uploadSetsContentTypeToApplicationJson() throws IOException {
        AwsS3Uploader uploader = spy(new AwsS3Uploader());
        AmazonS3Client mockS3Client = mock(AmazonS3Client.class);
        doReturn(mockS3Client).when(uploader).createClient(any());

        InputStream data = new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));
        uploader.upload(credentials("bucket", "key"), "file.geojson", data);

        ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(mockS3Client).putObject(any(), any(), any(), metadataCaptor.capture());

        assertThat(metadataCaptor.getValue().getContentType()).isEqualTo("application/json");
    }

    @Test
    public void uploadSetsContentLengthFromStreamAvailable() throws IOException {
        AwsS3Uploader uploader = spy(new AwsS3Uploader());
        AmazonS3Client mockS3Client = mock(AmazonS3Client.class);
        doReturn(mockS3Client).when(uploader).createClient(any());

        byte[] payload = "{\"type\":\"FeatureCollection\",\"features\":[]}".getBytes(StandardCharsets.UTF_8);
        InputStream data = new ByteArrayInputStream(payload);

        uploader.upload(credentials("bucket", "key"), "file.geojson", data);

        ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(mockS3Client).putObject(any(), any(), any(), metadataCaptor.capture());

        // ByteArrayInputStream.available() returns the full byte count
        assertThat(metadataCaptor.getValue().getContentLength()).isEqualTo(payload.length);
    }

    /**
     * Documents the known limitation: InputStream.available() returns 0 for
     * streams that do not pre-buffer (e.g. network streams, PipedInputStream).
     * This causes a zero Content-Length header to be sent to S3, which can result
     * in empty or rejected uploads.
     */
    @Test
    public void contentLengthIsZeroForNonBufferedStream() throws IOException {
        AwsS3Uploader uploader = spy(new AwsS3Uploader());
        AmazonS3Client mockS3Client = mock(AmazonS3Client.class);
        doReturn(mockS3Client).when(uploader).createClient(any());

        InputStream nonBufferedStream = new InputStream() {
            @Override
            public int read() {
                return -1;
            }

            @Override
            public int available() {
                return 0; // always 0 — simulates a network/piped stream
            }
        };

        uploader.upload(credentials("bucket", "key"), "file.geojson", nonBufferedStream);

        ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(mockS3Client).putObject(any(), any(), any(), metadataCaptor.capture());

        assertThat(metadataCaptor.getValue().getContentLength())
                .as("Content-Length is 0 for non-buffered streams due to InputStream.available() limitation")
                .isEqualTo(0L);
    }

    @Test
    public void createClientBuildsClientFromCredentials() {
        AwsS3Uploader uploader = new AwsS3Uploader();
        MapBoxAwsCredentials credentials = credentials("bucket", "key");
        credentials.setAccessKeyId("AKIAIOSFODNN7EXAMPLE");
        credentials.setSecretAccessKey("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        credentials.setSessionToken("session-token-value");

        AmazonS3Client client = uploader.createClient(credentials);

        assertThat(client).isNotNull();
    }

    // --- helper ---

    private MapBoxAwsCredentials credentials(String bucket, String key) {
        MapBoxAwsCredentials creds = new MapBoxAwsCredentials();
        creds.setBucket(bucket);
        creds.setKey(key);
        creds.setAccessKeyId("accessKeyId");
        creds.setSecretAccessKey("secretAccessKey");
        creds.setSessionToken("sessionToken");
        creds.setUrl("http://s3.amazonaws.com/" + bucket);
        return creds;
    }
}