#!/bin/sh
${0%/*}/build-guisso-env.sh
docker-compose -f "${0%/*}/docker-compose.yml" up -d planwisedb
sleep 10
# setup database
docker-compose -f "${0%/*}/docker-compose.yml" run --rm planwiseweb /bin/sh -c "sleep 5 && cd /app && scripts/migrate && scripts/import-osm && scripts/load-regions"
docker-compose -f "${0%/*}/docker-compose.yml" up -d
