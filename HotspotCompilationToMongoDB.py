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


s = commands.getoutput('java -XX:+PrintCompilation Harness luindex -s small')
#s = re.findall(r'.+', s)[0]
#print s
for d in re.findall(r'\s*(\d+)\s*(\d+)\s*([%s!bn]*)\s*(\d)\s*([\w\.\:\:]+)', s):
    #d[0] is timestamp
    #d[1] is compiled method id
    #d[2] contains compilation flags
    #d[3] is the teired compilation level
    #d[4] is the method descriptor

    post = {"optLevel":int(d[3].strip()),
            "methodFullDesc":d[4].strip ()}
    collection.insert_one(post)

client.close()
    
