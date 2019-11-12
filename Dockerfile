FROM openjdk:11-jre
WORKDIR /deployments
ADD target/asag-*-SNAPSHOT.jar asag.jar
RUN mkdir /root/.ssh \
 && touch /root/.ssh/known_hosts
CMD java $JAVA_OPTIONS -jar asag.jar
