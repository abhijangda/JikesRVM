import re
import os
import commands
import sys

try:
    import pymongo
except:
    print "To use install PyMongo using pip"
    print "sudo pip install pymongo"
    sys.exit(0)

client = pymongo.MongoClient ()
db = client.AOSDatabase
collection = db.AOSCollection


#s = commands.getoutput('/media/sda13/OpenJDK/openjdk_orig/openjdk/build/linux-x86_64-normal-server-release/jdk/bin/java -XX:+PrintCompilation Harness lusearch')
#s = commands.getoutput('/media/sda13/OpenJDK/openjdk_orig/openjdk/build/linux-x86_64-normal-server-release/jdk/bin/java -XX:+PrintCompilation -jar /media/sda13/OpenJDK/openjdk_orig/openjdk/build/linux-x86_64-normal-server-release/jdk/bin/dacapo.jar lusearch')
#s = re.findall(r'.+', s)[0]
s = commands.getoutput('/media/sda13/OpenJDK/openjdk_orig/openjdk/build/linux-x86_64-normal-server-release/jdk/bin/java -XX:+PrintCompilation -jar /home/abhi/glasgow/JikesRVM/dist/development_x86_64-linux/dacapo.jar lusearch -s large')
print s
for d in re.findall(r'\s*(\d+)\s*(\d+)\s*([%s!bn]*)\s*(\d)\s*([\<\>\$\w\.\:\:\_\d]+\([\$\[\w\/\;\_\d]*\)[\[\$\w\/\;\_\d]*) counts\:(\d+)', s):
    #d[0] is timestamp
    #d[1] is compiled method id
    #d[2] contains compilation flags
    #d[3] is the teired compilation level
    #d[4] is the method descriptor
    #d[5] is the counts
    print d
    desc = d[4].strip ()
    desc = 'L'+ desc.replace('::', ';').replace('.', '/')

    cursor = collection.find ({"methodFullDesc":desc})
    doc = None
    for document in cursor:
        doc = document
        #print "FOUND Document with optLevel ", doc["optLevel"], doc["methodFullDesc"]
        break
    
    if (doc == None):
        post = {"optLevel":int(d[3].strip()),
                "methodFullDesc":desc,
                "count":float(d[5])}
        collection.insert_one(post)
    elif doc["optLevel"] < int(d[3].strip()):
        collection.update_one({"methodFullDesc": desc},
                              {
                                  "$set": {
                                      "optLevel": int(d[3].strip())
                                      }
                                  })
        
client.close()
    
