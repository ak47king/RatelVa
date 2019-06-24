#!/usr/bin/env bash

now_dir=`pwd`
cd `dirname $0`
script_dir=`pwd`
cd ${now_dir}
jarsigner -verbose -storepass hermes -keystore ${script_dir}/hermes_key $* hermes