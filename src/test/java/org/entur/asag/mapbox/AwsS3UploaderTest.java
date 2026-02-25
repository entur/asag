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

import org.entur.asag.mapbox.model.MapBoxAwsCredentials;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class AwsS3UploaderTest {

    @Test
    public void uploadCallsPutObjectWithCredentialBucketAndKey() throws IOException {
        AwsS3Uploader uploader = spy(new AwsS3Uploader());
        S3Client mockS3Client = mock(S3Client.class);
        doReturn(mockS3Client).when(uploader).createClient(any());

        MapBoxAwsCredentials credentials = credentials("my-bucket", "my/key");
        InputStream data = new ByteArrayInputStream("{\"type\":\"FeatureCollection\"}".getBytes(StandardCharsets.UTF_8));

        uploader.upload(credentials, "entur.geojson", data);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(mockS3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("my-bucket");
        assertThat(requestCaptor.getValue().key()).isEqualTo("my/key");
    }

    @Test
    public void uploadSetsContentTypeToApplicationJson() throws IOException {
        AwsS3Uploader uploader = spy(new AwsS3Uploader());
        S3Client mockS3Client = mock(S3Client.class);
        doReturn(mockS3Client).when(uploader).createClient(any());

        InputStream data = new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));
        uploader.upload(credentials("bucket", "key"), "file.geojson", data);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(mockS3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        assertThat(requestCaptor.getValue().contentType()).isEqualTo("application/json");
    }

    @Test
    public void uploadSetsContentLengthFromReadAllBytes() throws IOException {
        AwsS3Uploader uploader = spy(new AwsS3Uploader());
        S3Client mockS3Client = mock(S3Client.class);
        doReturn(mockS3Client).when(uploader).createClient(any());

        byte[] payload = "{\"type\":\"FeatureCollection\",\"features\":[]}".getBytes(StandardCharsets.UTF_8);
        InputStream data = new ByteArrayInputStream(payload);

        uploader.upload(credentials("bucket", "key"), "file.geojson", data);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(mockS3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        assertThat(requestCaptor.getValue().contentLength()).isEqualTo((long) payload.length);
    }

    @Test
    public void createClientBuildsClientFromCredentials() {
        AwsS3Uploader uploader = new AwsS3Uploader();
        MapBoxAwsCredentials credentials = credentials("bucket", "key");
        credentials.setAccessKeyId("AKIAIOSFODNN7EXAMPLE");
        credentials.setSecretAccessKey("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        credentials.setSessionToken("session-token-value");

        S3Client client = uploader.createClient(credentials);

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
