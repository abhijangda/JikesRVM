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
    cursor = collection.find ({"methodFullDesc":d[4].strip()})
    doc = None
    for document in cursor:
        doc = document
        break
    desc = d[4].strip ()
    desc = '/'+ desc.replace('::', '.').replace('.', '/')

    if (doc == None):
        post = {"optLevel":int(d[3].strip()),
                "methodFullDesc":desc}
        collection.insert_one(post)
    elif doc["optLevel"] < int(d[3].strip()):
        collection.update_one({"methodFullDesc": desc},
                              {
                                  "$set": {
                                      "optLevel": int(d[3].strip())
                                      }
                                  })
        
client.close()
    
