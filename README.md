# asag
Update mapbox tilset with netex stop place data 


## Get hold of the generated file when running on server

With kubernetes. Execute the following while the mapbox job is running and has finished creating the geojson file. This can be determined by looking at the logs, i.e: `Upload: {"tileset":"entur.carbon-1287"....`.
```kc cp mapbox-update-job:/deployments/files/mapbox/carbon-1287.geojson .```
