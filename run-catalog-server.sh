if [[ ${@: -1} == "clean" ]]
then
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

    echo "Cleanup done. Exiting!"
    exit 0
fi

UI_IP=$1
INDEX=${@:(-1)}

CP=$(find lib/ -iname "*.jar" -exec readlink -f {} \; | tr '\n' ':')

PWD=`pwd`
INITDB="$PWD/initDB"
CATALOG_WAL="$PWD/CatalogServer$INDEX.WAL"
CATALOG_OUTPUT="$PWD/catalog-server$INDEX.out"

cd bin/
java -cp $CP:. pygmy.com.catalog.CatalogServer $INITDB $CATALOG_WAL $UI_IP pygmy/com/catalog/CatalogServer.class > $CATALOG_OUTPUT 2>&1 &
