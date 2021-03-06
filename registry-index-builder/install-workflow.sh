#!/usr/bin/env bash
#exit on any failure
set -e

P=$1
TOKEN=$2

echo "Getting latest registry-index-builder workflow properties file from github"
curl -s -H "Authorization: token $TOKEN" -H 'Accept: application/vnd.github.v3.raw' -O -L https://api.github.com/repos/gbif/gbif-configuration/contents/registry-index-builder/$P.properties

#extract the oozie.url value from the properties file
oozie_url=`cat $P.properties| grep "oozie.url" | cut -d'=' -f2-`
solr_url=`cat $P.properties| grep "solr.url" | cut -d'=' -f2-`
solr_collection=`cat $P.properties| grep "solr.dataset.collection" | cut -d'=' -f2-`
solr_opts=`cat $P.properties| grep "solr.opts" | cut -d'=' -f2-`
solr_home=`cat $P.properties| grep "solr.home" | cut -d'=' -f2-`
zk_host=`cat $P.properties| grep "solr.dataset.home" | cut -d'=' -f2-`
namenode=`cat $P.properties| grep "namenode" | cut -d'=' -f2-`

echo "Assembling jar for $P"

mvn -U -Poozie clean package -DskipTests assembly:single

if hdfs dfs -test -d /registry-index-builder-$P/; then
   echo "Removing content of current Oozie workflow directory"
   sudo -u hdfs hdfs dfs -rm -f -r /registry-index-builder-$P/*
else
   echo "Creating workflow directory"
   sudo -u hdfs hdfs dfs -mkdir /registry-index-builder-$P/
fi
echo "Copying new Oozie workflow to HDFS"
sudo -u hdfs hdfs dfs -copyFromLocal target/oozie-workflow/* /registry-index-builder-$P/
sudo -u hdfs hdfs dfs -copyFromLocal $P.properties /registry-index-builder-$P/lib/

echo "Delete existing collection"
curl -s """${solr_url}"/admin/collections?action=DELETE\&name="${solr_collection}"""

echo "Copy solr configs to Zookeeper"
${solr_home}/server/scripts/cloud-scripts/zkcli.sh  -zkhost ${zk_host} -cmd upconfig -confname ${solr_collection} -confdir ../registry-ws/src/main/resources/solr/dataset/conf/

echo "Create collection"
curl -s """${solr_url}"/admin/collections?action=CREATE\&name="${solr_collection}"\&"${solr_opts}"\&collection.configName="${solr_collection}"""

echo "Running Oozie workflow"
sudo -E -u hdfs oozie job --oozie ${oozie_url} -config $P.properties -D oozie.wf.application.path=${namenode}/registry-index-builder-$P/ -run
