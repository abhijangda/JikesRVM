#!/bin/sh

# This script should contain all the steps needed to get Jikes to compile on a
# Ubuntu 14.04 LTS system such as that running on the GPG cluster.
#
# * Set up a new binary directory in home/ (by default) and adds this to the PATH
# * Compiles a number of dependencies from downloads
# * Clones our Jikes repository and applies some local configuration changes
#
# Some steps, such as adding the PATH and starting the MongoDB server may have
# to be done manually before running experiments.


# === Definitions ===

# Paths
USER=`whoami`
ABS_HOME=`cd ~ && pwd`
PREFIX="${HOME}/gpg-bin/"
MONGO_DBPATH="/tmp/${USER}/jikes-mongo/db"

# Path to Jikes Repository
JIKES_REPO=https://github.com/abhijangda/JikesRVM.git

# Package Versions
ANT_VERSION=apache-ant-1.9.6
GETTEXT_VERSION=gettext-0.19
BISON_VERSION=bison-3.0.4
MONGO_VERSION=mongodb-linux-x86_64-2.4.14

# === Create directories and set PATH ===
mkdir -p $PREFIX/bin/
export PATH=$PREFIX/bin:$PATH

# === Install build dependencies and tools ===
cd $PREFIX

# Ant
curl -O http://www.mirrorservice.org/sites/ftp.apache.org/ant/binaries/$ANT_VERSION-bin.tar.gz
tar -zxvf $ANT_VERSION-bin.tar.gz
ln -s $ANT_VERSION-bin/bin/ant $PREFIX/bin/

# gettext library
curl -O -L http://ftpmirror.gnu.org/gettext/$GETTEXT_VERSION.tar.gz
tar -zxvf $GETTEXT_VERSION.tar.gz
cd $GETTEXT_VERSION
./configure --prefix=$PREFIX
make install
cd ..

# bison
curl -O -L http://ftpmirror.gnu.org/bison/$BISON_VERSION.tar.gz
tar -zxvf $BISON_VERSION.tar.gz
cd $BISON_VERSION
./configure --prefix=$PREFIX
make install
cd ..

# === Install and start database server ===
curl -O http://downloads.mongodb.org/linux/$MONGO_VERSION.tgz
tar -zxvf $MONGO_VERSION.tgz
ln -s $MONGO_VERSION/bin/mongod $PREFIX/bin/
ln -s $MONGO_VERSION/bin/mongo $PREFIX/bin/

# === Get Jikes Repository ===
cd ~
git clone $JIKES_REPO JikesRVM
git checkout experiments

# === Apply fixes to Jikes Repository ===
cd JikesRVM

# Correct the path to bison binary
patch build/host/x86_64-linux.properties <<EOF
@@ -13,1 +13,1 @@
-bison.exe=/usr/bin/bison
+bison.exe=${PREFIX}/bin/bison
EOF

# Comment out autogen.sh task in build script for classpath library
patch build/components/classpath.xml <<'EOF'
@@ -189,3 +189,3 @@
-    -->
     <exec executable="${classpath.dir}/autogen.sh" failonerror="true" dir="${classpath.dir}"/>
+    -->
EOF

# Download JUnit and set related properties
mkdir -p components/junit/4.10/
echo 'junit.jar=components/junit/4.10/junit4.10/junit-4.10.jar' >> .ant.properties

# Download and unzip JUnit
cd components/junit/4.10/
wget http://sourceforge.net/projects/junit/files/junit/4.10/junit4.10.zip/download?use_mirror=autoselect -O junit4.10.zip -q
unzip junit4.10.zip

# Tell ant not to download it again
cd junit4.10
echo 'junit.version=4.10' > constants.properties
echo 'junit.description=jUnit' >> constants.properties

# Back to ~/JikesRVM/
cd ../../../../

# === Compile Jikes ===

# compile classpath library
ant -f build/components/classpath.xml -Dhost.name=x86_64-linux -Dconfig.name=development

# compile Jikes
ant -Dhost.name=x86_64-linux -Dconfig.name=development

echo <<EOF
Remember to set the PATH on every new shell: export PATH=$PREFIX/bin:\$PATH
Start the mongo server: (mongod --dbpath $MONGO_DBPATH </dev/null >/dev/null 2>&1) &
Run experiments: cd experiments && python ./1_naive_overhead.py
EOF
