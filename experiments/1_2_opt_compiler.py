import common
import csv
import os
import sys

# 1.2: This quick test simply tries to run the benchmarks with the optimizing
# compiler - here every method will be compiled by the opt compiler immediately
# the first time it is used.

benchmarks = ['avrora', 'lusearch', 'jython', 'luindex', 'xalan', 'pmd', 'sunflow']

results_dir = os.path.abspath('results')
results_prefix = '1_2_'
n = 2

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
            r.writerow(['i', 'normal'])

            for i in range(n):
                time = common.run_dacapo(b, vm_args=['-X:aos:initial_compiler=opt'], timelimit=180)
                r.writerow([i, str(time)])
except Exception as e:
    print e
    raw_input("Continue?")
finally:
    common.teardown()
