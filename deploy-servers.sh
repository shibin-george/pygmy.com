CATALOG_SERVER="elnux1.cs.umass.edu"
UI_SERVER="elnux3.cs.umass.edu"
ORDER_SERVER="elnux7.cs.umass.edu"

UI_IP="128.119.243.168"
CATALOG_IP="128.119.243.147"
ORDER_IP="128.119.243.175"

if [[ ${@: -1} == "clean" ]]
then
  CLEAN="clean"
fi

chmod +x *.sh && ./compile.sh

# set classpath
CP=$(find lib/ -iname "*.jar" -exec readlink -f {} \; | tr '\n' ':')

PWD=`pwd`

# Catalog server
ssh $USER@$CATALOG_SERVER "cd $PWD && ./run-catalog-server.sh $CLEAN"
ssh $USER@$CATALOG_SERVER "cd $PWD && ps -ef > ps.cat" 
if grep -q "pygmy.com.catalog.CatalogServer" ps.cat;
then
  echo "Catalog Server is running on $CATALOG_SERVER @ $CATALOG_IP !"
else 
  echo "Catalog Server in not running! Check this before proceeding!!"
fi

# UI server
ssh $USER@$UI_SERVER "cd $PWD && ./run-ui-server.sh $CATALOG_IP $ORDER_IP $CLEAN"
ssh $USER@$UI_SERVER "cd $PWD && ps -ef > ps.ui"
if grep -q "pygmy.com.ui.UIServer" ps.ui;
then
  echo "UI Server is running on $UI_SERVER @ $UI_IP !"
else
  echo "UI Server is not running! Check this before proceeding!!"
fi

# Order server
ssh $USER@$ORDER_SERVER "cd $PWD && ./run-order-server.sh $CATALOG_IP $CLEAN"
ssh $USER@$ORDER_SERVER "cd $PWD && ps -ef > ps.ord"
if grep -q "pygmy.com.order.OrderServer" ps.ord;
then
  echo "Order Server is running on $ORDER_SERVER @ $ORDER_IP !"
else
  echo "Order Server is not running! Check this before proceeding!!"
fi
