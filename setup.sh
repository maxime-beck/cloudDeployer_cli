#/bin/bash!

git clone https://github.com/web-servers/tomcat-in-the-cloud.git
cd tomcat-in-the-cloud
sh ./setup.sh
mvn install
