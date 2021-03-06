FROM openjdk:8-jre

# Install package dependencies and add precompiled binary
RUN for i in {1..5}; do \
       (apt-get update \
        && apt-get -y install postgresql-client libboost-program-options-dev libpq-dev gdal-bin python-gdal \
        && break) \
       || (sleep 5; false); done \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*
ADD docker/osm2pgrouting /usr/local/bin/osm2pgrouting

# Add scripts
ADD scripts/* /app/scripts/
ENV SCRIPTS_PATH /app/scripts/

# Add project compiled binaries
ADD cpp/calculate-demand /app/bin/calculate-demand
ADD cpp/aggregate-population /app/bin/aggregate-population
ENV BIN_PATH /app/bin/

# Add uberjar with app
ADD ./target/uberjar/planwise-standalone.jar /app/lib/
ENV JAR_PATH /app/lib/planwise-standalone.jar

# Exposed port
ENV PORT 80
EXPOSE $PORT

# Data and tmp folders
ENV DATA_PATH /data/
ENV TMP_PATH /tmp/

CMD ["java", "-jar", "/app/lib/planwise-standalone.jar"]
