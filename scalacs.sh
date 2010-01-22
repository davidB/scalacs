#! /bin/sh

URL=http://127.0.0.1:27616
case $1 in
  mvn-start)
    mvn compile scala:run -DmainClass=net_alchim31_scalacs.HttpServer
    ;;
  start)
    java -jar target/scalacs-0.1-withDeps.jar
    ;;
  add)
    # shortcut (and for backward compatibility)
    curl --data-binary @$2 ${URL}/createOrUpdate
    ;;
  createOrUpdate)
    curl --data-binary @$2 ${URL}/createOrUpdate
    ;;
  *)
    # $1 support compile, clean, reset, delete
    curl ${URL}/$1/$2
    ;;
esac