#/bin/bash!

git clone https://github.com/web-servers/cloudStreamProvider.git
mvn -f cloudStreamProvider/pom.xml install
git clone https://github.com/web-servers/cloudMemberProvider.git
mvn -f cloudMemberProvider/pom.xml install
git clone https://github.com/web-servers/tomcat-in-the-cloud.git
mvn -f tomcat-in-the-cloud/pom.xml install
