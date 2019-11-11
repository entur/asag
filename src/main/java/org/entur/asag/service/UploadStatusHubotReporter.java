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

import org.apache.camel.Body;
import org.apache.camel.ExchangeProperty;
import org.entur.asag.mapbox.model.MapBoxUploadStatus;
import org.rutebanken.helper.slack.SlackPostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static org.entur.asag.mapbox.MapBoxUpdateRouteBuilder.PROPERTY_STATE;

@Service
public class UploadStatusHubotReporter {

    private static final Logger logger = LoggerFactory.getLogger(UploadStatusHubotReporter.class);

    private static final String ICON = ":world_map:";

    private static final String OK_ICON = ":golfer:";

    private static final String WARN_ICON = ":fire:";

    @Autowired
    private SlackPostService hubotPostService;

    @Value("${HOSTNAME:asag}")
    private String hostName;


    public void postStarted() {

        hubotPostService.publish(new SlackPostService.SlackPayload("Started mapbox update" + hostName + ICON));
    }

    public void postUploadStatusToHubot(@Body MapBoxUploadStatus mapBoxUploadStatus, @ExchangeProperty(PROPERTY_STATE) String state) {

        String message;

        if (mapBoxUploadStatus.getComplete()) {
            message = "Tilset `" + mapBoxUploadStatus.getTileset() + "` uploaded to mapbox successfully " + OK_ICON + ".";
        } else {
            message = state != null ? "*" + state + "*: " : "";
            message += "Upload `" + mapBoxUploadStatus.getId()
                    + "`, name: `" + mapBoxUploadStatus.getName()
                    + "`, tileset: `" + mapBoxUploadStatus.getTileset()
                    + "`, message: `" + mapBoxUploadStatus.getMessage()
                    + "`, error: " + mapBoxUploadStatus.getError()
                    + " " + WARN_ICON;
        }

        logger.info("About to post message to hubot: {}", message);

        hubotPostService.publish(new SlackPostService.SlackPayload(message + hostName + ICON));
    }
}
