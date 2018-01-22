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

package org.entur.asag.mapbox.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MapBoxAwsCredentials {

    @JsonProperty("bucket")
    private String bucket;
    @JsonProperty("key")
    private String key;
    @JsonProperty("accessKeyId")
    private String accessKeyId;
    @JsonProperty("secretAccessKey")
    private String secretAccessKey;
    @JsonProperty("sessionToken")
    private String sessionToken;
    @JsonProperty("url")
    private String url;

    @JsonProperty("bucket")
    public String getBucket() {
        return bucket;
    }

    @JsonProperty("bucket")
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    @JsonProperty("key")
    public String getKey() {
        return key;
    }

    @JsonProperty("key")
    public void setKey(String key) {
        this.key = key;
    }

    @JsonProperty("accessKeyId")
    public String getAccessKeyId() {
        return accessKeyId;
    }

    @JsonProperty("accessKeyId")
    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    @JsonProperty("secretAccessKey")
    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    @JsonProperty("secretAccessKey")
    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    @JsonProperty("sessionToken")
    public String getSessionToken() {
        return sessionToken;
    }

    @JsonProperty("sessionToken")
    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    @JsonProperty("url")
    public String getUrl() {
        return url;
    }

    @JsonProperty("url")
    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("bucket", bucket)
                .add("key", key)
                .add("accessKeyId", accessKeyId)
                .add("secretAccessKey", secretAccessKey)
                .add("sessionToken", sessionToken)
                .add("url", url)
                .toString();
    }
}