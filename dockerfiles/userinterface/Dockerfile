FROM openjdk:13-alpine
 
# copy the packaged jar file into our docker image
COPY ./UserInterface.jar /UserInterface.jar
COPY ./docker-user-interface.sh /docker-user-interface.sh

RUN apk add --no-cache bash

CMD ["/bin/bash"]
