# kill all running catalog instances
ps -ef | grep "pygmy\.com\.catalog\." | while read -r line ; do
    pid=$( echo $line | cut -d " " -f 2 )
    kill -9 $pid 2>/dev/null
done

# kill all running ui instances
ps -ef | grep "pygmy\.com\.ui\." | while read -r line ; do
    pid=$( echo $line | cut -d " " -f 2 )
    kill -9 $pid 2>/dev/null
done

# kill all running order instances
ps -ef | grep "pygmy\.com\.order\." | while read -r line ; do
    pid=$( echo $line | cut -d " " -f 2 )
    kill -9 $pid 2>/dev/null
done

if [[ ${@: -1} == "clean" ]]
then
  echo "Cleanup done. Exiting!"
  exit 0
fi

CP=$(find lib/ -iname "*.jar" -exec readlink -f {} \; | tr '\n' ':')

PWD=`pwd`
UI_OUTPUT="$PWD/ui-server.out"

cd bin/
java -cp $CP:. pygmy.com.ui.UIServer $1 $2 pygmy/com/ui/UIServer.class > $UI_OUTPUT 2>&1 &
