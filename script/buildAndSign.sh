#!/usr/bin/env bash

now_dir=`pwd`
cd `dirname $0`
script_dir=`pwd`
cd ..
./gradlew rebuilder:shadowJar

if [ $? != 0 ] ;then
    echo "builder jar assemble failed"
    exit $?
fi
builder_jar=`pwd`/rebuilder/build/libs/container-builder-va-1.0.0.jar

if [ -f ${builder_jar} ] ;then
    echo "use ${builder_jar}"
else
    echo "can not find container build jar in path:${builder_jar}"
    echo -1
fi

cd ${now_dir}

rm -rf dist/*

echo "assemble new apk for $*"
java -jar ${builder_jar} $*
if [ $? != 0 ] ;then
    echo "assemble ratel apk failed"
    exit $?
fi

if [ -f "${script_dir}/hermes_key" ] ;then
    echo "auto sign output apk"
else
    echo "can not find apk sign key failed"
    exit -1
fi


for file in dist/*
do
    if [ -f $file ] ;then
        jarsigner -verbose -storepass hermes -keystore ${script_dir}/hermes_key ${file} hermes
    fi
done