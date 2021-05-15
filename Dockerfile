FROM adoptopenjdk/openjdk11:alpine-jre
WORKDIR /deployments
COPY target/asag-*-SNAPSHOT.jar asag.jar
RUN addgroup appuser && adduser --disabled-password appuser --ingroup appuser
chown -R appuser:appuser /deployments
USER appuser
RUN mkdir -p /home/appuser/.ssh \
 && touch /home/appuser/.ssh/known_hosts
CMD java $JAVA_OPTIONS -jar asag.jar
