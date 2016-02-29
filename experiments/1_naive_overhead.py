import common
import csv
import os
import sys

# This experiment measures the overhead incurred when using naive mongodb calls in the AOS
# update methods. The -use_aosdb flag is enabled on the master branch build.

benchmarks = ['avrora', 'lusearch', 'jython', 'luindex', 'xalan', 'pmd', 'sunflow']

results_dir = os.path.abspath('results')
results_prefix = '1_'
n = 3
TIMELIMIT = 60

# Allow overriding the number of repetitions for each benchmark pair with the first argument
if (len(sys.argv) > 1):
    try:
        n = int(sys.argv[1])
    except Exception:
        print "ERROR", "The first argument sets the number of repetitions and must be positive integer."
        exit(1)
try:
    common.checkout_and_build_jikes('master')

    if not os.path.exists(results_dir):
        os.makedirs(results_dir)

    for b in benchmarks:
        csvname = os.path.abspath(os.path.join(results_dir, results_prefix+b+'.csv'))

        print '----', 'Running', b, n, 'times, results in', csvname, '----'

        with open(csvname, 'wb') as csvfile:
            r = csv.writer (csvfile, delimiter = ',')
            r.writerow(['i', 'normal', 'use_aosdb'])

            for i in range(n):
                normal_time = common.run_dacapo(b, timelimit=TIMELIMIT)
                aos_time = common.run_dacapo(b, vm_args=['-use_aosdb'], timelimit=TIMELIMIT)
                r.writerow([i, str(normal_time), str(aos_time)])
except Exception as e:
    print e
    raw_input("Continue?")
finally:
    common.teardown()
