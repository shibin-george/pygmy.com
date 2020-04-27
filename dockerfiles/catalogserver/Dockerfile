FROM openjdk:13-alpine
 
# copy the packaged jar file into our docker image
COPY ./CatalogServer.jar /CatalogServer.jar
COPY ./initDB /initDB
 
# set the startup command to execute the jar
CMD ["java", "-jar", "/CatalogServer.jar", "/initDB", "/CatalogServer.WAL", "172.18.0.22"]
