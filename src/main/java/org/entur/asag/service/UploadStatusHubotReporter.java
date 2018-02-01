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
import org.entur.asag.mapbox.model.MapBoxUploadStatus;
import org.rutebanken.helper.hubot.HubotPostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class UploadStatusHubotReporter {

    private static final Logger logger = LoggerFactory.getLogger(UploadStatusHubotReporter.class);

    private static final String ICON = ":world_map:";

    private static final String WARN_ICON = ":fire:";

    @Autowired
    private HubotPostService hubotPostService;

    @Value("${HOSTNAME:asag}")
    private String hostName;


    public void postStarted() {
        hubotPostService.publish(new HubotPostService.HubotMessage("Started mapbox update", hostName, ICON));
    }


    public void postUploadStatusToHubot(@Body MapBoxUploadStatus mapBoxUploadStatus) {

        final String pre = "Upload " + mapBoxUploadStatus.getId() + ", name: " + mapBoxUploadStatus.getName() + ",  tileset: " + mapBoxUploadStatus.getTileset();
        final String message;

        if (mapBoxUploadStatus.getComplete()) {
            message = pre + " complete";
        } else {
            message = pre + " " +WARN_ICON + " message: `" + mapBoxUploadStatus.getMessage() + "`, error: " + mapBoxUploadStatus.getError();
        }

        logger.info("About to post message to hubot: {}", message);


        hubotPostService.publish(new HubotPostService.HubotMessage(message, hostName, ICON));
    }
}
