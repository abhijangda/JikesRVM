import os
import commands
import re
import csv

benchmarks= ['avrora', 'lusearch', 'jython', 'luindex', 'xalan', 'pmd', 'sunflow']

with open ('mongo_results_with_async.csv', 'wb') as csvfile:
    os.chdir ('../dist/development_x86_64-linux')
    r = csv.writer (csvfile, delimiter = ' ')
    r.writerow (['Benchmark', 'NormalTime', 'MongoTime', 'Overhead (%)'])
    
    for b in benchmarks:
        print "Running ", b, "normally"
        o = commands.getoutput ('./rvm -Xbootclasspath/p:mongo-java-driver-2.11.3.jar -jar dacapo.jar ' + b)
        normal_time = int (re.findall (r'PASSED in (\d+)', o)[0])
        print normal_time
        
        print "Running ", b, "with -use_aosdb"
        o = commands.getoutput ('./rvm -use_aosdb10 -Xbootclasspath/p:mongo-java-driver-2.11.3.jar -jar dacapo.jar ' + b)
        aos_time = int (re.findall (r'PASSED in (\d+)', o)[0])
        print aos_time

        r.writerow ([b, str(normal_time), str (aos_time), str((aos_time-normal_time)*100/float(normal_time))])

    
    
    
