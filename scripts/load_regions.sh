#! /bin/bash
set -euo pipefail

COUNTRY=${1:-kenya}
LEVELS=${2:-2,4}
echo "Importing $COUNTRY at levels $LEVELS"

mkdir -p data

if [[ ! -e data/$COUNTRY ]]; then
  curl -o data/${COUNTRY}_geojson.tgz https://s3.amazonaws.com/osm-polygons.mapzen.com/${COUNTRY}_geojson.tgz
  tar -xzf data/${COUNTRY}_geojson.tgz -C data
fi

IFS=',';
for i in $LEVELS; do
FILE=data/${COUNTRY}/admin_level_${i}.geojson
echo " Processing $FILE"
psql -d $POSTGRES_DB -U $POSTGRES_USER -h $POSTGRES_HOST << SQL_SCRIPT

  WITH data AS (SELECT \$$`cat $FILE`\$$::json AS fc)
  INSERT INTO "regions" (country, name, admin_level, the_geom)
  SELECT
    '${COUNTRY}',
    feat#>>'{properties,name}' AS name,
    (feat#>>'{properties,admin_level}')::integer AS admin_level,
    ST_SetSRID(ST_GeomFromGeoJSON(feat->>'geometry'), 4326) as the_geom
  FROM (
    SELECT json_array_elements(fc->'features') AS feat
    FROM data
  ) AS f;

SQL_SCRIPT
done;
