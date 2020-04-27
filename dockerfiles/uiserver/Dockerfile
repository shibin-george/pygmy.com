FROM openjdk:13-alpine
 
# copy the packaged jar file into our docker image
COPY ./UIServer.jar /UIServer.jar
 
# set the startup command to execute the jar
CMD ["java", "-jar", "/UIServer.jar"]
