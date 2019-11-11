# asag [![CircleCI](https://circleci.com/gh/entur/asag/tree/master.svg?style=svg)](https://circleci.com/gh/entur/asag/tree/master)
Update mapbox tilset with netex stop place data


## Get hold of the generated file when running on server

With kubernetes. Execute the following while the mapbox job is running and has finished creating the geojson file. This can be determined by looking at the logs, i.e: `Upload: {"tileset":"entur.carbon-1287"....`.
```kc cp mapbox-update-job:/deployments/files/mapbox/carbon-1287.geojson .```

## Job specifications
The kuberenetes job spec is located in babylon:
https://github.com/entur/babylon/blob/master/src/main/resources/jobs/production/mapbox-update-pod.yaml
https://storage.googleapis.com/marduk-production/tiamat/Current_latest.zip