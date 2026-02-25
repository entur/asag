FROM eclipse-temurin:25-jre-alpine
WORKDIR /deployments
COPY target/asag-*-SNAPSHOT.jar asag.jar
RUN addgroup appuser && adduser --disabled-password appuser --ingroup appuser
RUN chown -R appuser:appuser /deployments
USER appuser
RUN mkdir -p /home/appuser/.ssh \
 && touch /home/appuser/.ssh/known_hosts
CMD  [ "java", "-jar", "asag.jar"]
