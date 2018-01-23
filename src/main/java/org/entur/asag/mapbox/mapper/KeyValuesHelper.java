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

import org.rutebanken.netex.model.DataManagedObjectStructure;
import org.rutebanken.netex.model.KeyValueStructure;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class KeyValuesHelper {


    public static Optional<String> getValueByKey(DataManagedObjectStructure dataManagedObject, String key) {

        return Stream.of(dataManagedObject)
                .filter(Objects::nonNull)
                .map(DataManagedObjectStructure::getKeyList)
                .filter(Objects::nonNull)
                .flatMap(keyList -> keyList.getKeyValue().stream())
                .filter(keyValueStructure -> keyValueStructure.getKey().equals(key))
                .map(KeyValueStructure::getValue)
                .flatMap(Stream::of)
                .findFirst();
    }


}
