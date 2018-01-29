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

package org.entur.asag.mapbox.mapper;

import com.google.common.base.Strings;
import org.geojson.Feature;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.PrivateCodeStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.function.BiConsumer;

public class MapperHelper {

    public static final String LANG = "Lang";
    private static final Logger logger = LoggerFactory.getLogger(MapperHelper.class);

    public static void mapMultilingualString(String property, Feature feature, MultilingualString multilingualString) {
        if (multilingualString != null) {
            feature.setProperty(property, multilingualString.getValue());
            if (multilingualString.getLang() != null) {
                feature.setProperty(property + LANG, multilingualString.getLang());
            }
        }
    }


    public static void setIfNotNull(String key, String value, BiConsumer<String, String> consumer) {
        if (!Strings.isNullOrEmpty(value)) {
            consumer.accept(key, value);
        }
    }

    public static void setIfNotNull(String key, BigInteger value, BiConsumer<String, String> consumer) {
        if (value != null) {
            consumer.accept(key, value.toString());
        }
    }

    public static void setPrivateCode(String key, PrivateCodeStructure privateCodeStructure, BiConsumer<String, String> keyValue) {
        if (privateCodeStructure != null) {
            if (!Strings.isNullOrEmpty(privateCodeStructure.getValue())) {
                keyValue.accept(key, privateCodeStructure.getValue());
            }
        }
    }

    public static void setResolvedValue(String key, Object enumObject, BiConsumer<String, String> keyValue) {
        if (enumObject != null) {
            Object value = getEnumValue(enumObject);
            if (value != null) {
                keyValue.accept(key, String.valueOf(value));
            }
        }
    }

    public static Object getEnumValue(Object enumObject) {
        try {
            return enumObject.getClass().getMethod("value", null).invoke(enumObject);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            logger.warn("Error resolving value from enum {}", enumObject, e);
        }
        return null;
    }
}
