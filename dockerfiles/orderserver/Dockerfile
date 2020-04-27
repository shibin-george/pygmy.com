FROM openjdk:13-alpine
 
# copy the packaged jar file into our docker image
COPY ./OrderServer.jar /OrderServer.jar
 
# set the startup command to execute the jar
CMD ["java", "-jar", "/OrderServer.jar", "172.18.0.22", "/OrderServer.WAL"]
