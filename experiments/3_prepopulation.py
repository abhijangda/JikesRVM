import common
import csv
import os
import sys

# This experiment runs the same benchmark twice, to observe the behavior with an empty external database,
# and subsequently with a populated database.

benchmarks = ['avrora', 'lusearch', 'jython', 'luindex', 'xalan', 'pmd', 'sunflow']
git_checkout = 'BulkRead'

results_dir = os.path.abspath('results')
results_prefix = '3_'
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
    common.dropMongoCollection('AOSDatabase', 'AOSCollection')
    common.checkout_and_build_jikes(git_checkout)

    if not os.path.exists(results_dir):
        os.makedirs(results_dir)

    for b in benchmarks:
        csvname = os.path.abspath(os.path.join(results_dir, results_prefix+b+'.csv'))

        print '----', 'Running', b, n, 'times, results in', csvname, '----'

        with open(csvname, 'wb') as csvfile:
            r = csv.writer (csvfile, delimiter = ',')
            r.writerow(['i', 'base1', 'base2', 'vm1', 'vm2'])

            for i in range(n):
                print '---', b, i+1, 'of', n, '---'
                results = []

                # Clean MongoDB collection
                common.dropMongoCollection('AOSDatabase', 'AOSCollection')

                # TODO maybe wait - to ensure this has finished and isn't taking up resources/blocking DB operations

                # Baseline runs
                for k in range(2):
                    print '--', b, 'Baseline', k + 1, '--'
                    results += [common.run_dacapo(b, timelimit=TIMELIMIT)]

                # AOS database enabled runs
                for k in range(2):
                    print '--', b, 'AOSDB VM', k + 1, '--'
                    results += [common.run_dacapo(b, vm_args=['-use_aosdb1000', '-use_aosdbread'], timelimit=TIMELIMIT)]

                # convert all results to strings and store them
                r.writerow([i] + map(str, results))
except Exception as e:
    print e
    raw_input("Continue?")
finally:
    common.teardown()
