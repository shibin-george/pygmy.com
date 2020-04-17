# set classpath
CP=$(find lib/ -iname "*.jar" -exec readlink -f {} \; | tr '\n' ':')

cd bin/

# run user-interface
java -cp $CP:. userinterface.UserInterface 128.119.243.168 userinterface/UserInterface.class
