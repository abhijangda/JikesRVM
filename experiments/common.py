# python 2.7
# beyond build requirements for Jikes, requires installation of the timelimit utility.

import csv
import os
import re
import shutil
import socket
import string
import subprocess
import sys
import tempfile
import time
import urllib
import argparse

# === configuration options ===
__DACAPO_DOWNLOAD__ = 'http://downloads.sourceforge.net/project/dacapobench/9.12-bach/dacapo-9.12-bach.jar?r=http%3A%2F%2Fsourceforge.net%2Fprojects%2Fdacapobench%2Ffiles%2F9.12-bach%2F&ts=1455100129&use_mirror=kent'
__MONGO_DRIVER_JAR__ = 'mongo-java-driver-2.11.3.jar'

# === 'static' variables needed by module functions below - do not change ===
# state taken from funciton or program arguments
__NUM_REPETITIONS__ = 3
__TIMELIMIT__ = 0
__PREFIX__ = 'none'
__CSV_FILE__ = None
__CSV_WRITER__ = None

# set a temporary repository root if an experiment requires a specific commit checked out
__JIKES_EXPERIMENT_TEMP_ROOT__ = ''
__JIKES_EXPERIMENT_TEMP_BIN__ = ''

# keep the original repository root constant for returning here after teardown.
__JIKES_EXPERIMENT_ORIGINAL_ROOT__ = os.path.abspath(os.path.join(os.path.dirname(__file__),'..'))

def reset_root(root=__JIKES_EXPERIMENT_ORIGINAL_ROOT__):
    ''' for manual inclusion of common.py, this function can be called to set the temp-root and temp-bin
    paths to be within the original root directory or any other check out of the repository. '''
    global __JIKES_EXPERIMENT_TEMP_ROOT__, __JIKES_EXPERIMENT_TEMP_BIN__
    __JIKES_EXPERIMENT_TEMP_ROOT__ = root
    __JIKES_EXPERIMENT_TEMP_BIN__ = os.path.join(root, 'dist/development_x86_64-linux/')

def init(prefix, commit, timelimit=0):
    global __NUM_REPETITIONS__, __PREFIX__, __RESULTS_DIR__, __TIMELIMIT__, __ARGS__

    __TIMELIMIT__ = timelimit
    __ARGS__ = parse_arguments()

    if not __ARGS__.reuse_root:
        # Finally we need to build the Jikes binaries in a temporary checkout location
        checkout_and_build_jikes(commit)
    else:
        # Or re-use an existing repository checkout with pre-built binaries.
        reset_root(root=__ARGS__.reuse_root)

    if __ARGS__.compile_only:
        exit()

    # The prefix identifies the experiment.
    # We create a name for our results directory based on this, the system hostname, and current time.
    __PREFIX__ = prefix
    hostname = socket.gethostname()
    timestamp = int(time.time())
    __RESULTS_DIR__ = os.path.abspath(os.path.join('results', string.join([__PREFIX__, '-', hostname, '-', str(timestamp)], '')))
    print 'Results will be stored in', __RESULTS_DIR__

    if not os.path.exists(__RESULTS_DIR__):
        os.makedirs(__RESULTS_DIR__)

def parse_arguments():
    parser = argparse.ArgumentParser()
    parser.add_argument('--no-delete', action='store_true', help='Do not delete the temporary folder after completing the experiment. Useful to manually re-run some tests.')
    parser.add_argument('--compile-only', action='store_true', help='Build jikes and exit. Implies --no-delete.')
    parser.add_argument('--reuse-root', help='Pass the path to a previously built Jikes repository root to re-use binaries from there instead of re-building. Implies --no-delete')
    parser.add_argument('-n', help='override the number of repetitions')
    parser.add_argument('-t', help='override the time limit for each benchmark (seconds)')

    args = parser.parse_args()

    if args.reuse_root or args.compile_only:
        args.no_delete = True

    return args

def get_repetitions():
    ''' get the number of repetitions set by default or from a command line argument in init. '''
    return __NUM_REPETITIONS__

def checkout_and_build_jikes(commit):
    ''' creates a folder with a fresh build of the given commit hash
    returns the path to the temporary repository root
    changes into directory.'''
    global __JIKES_EXPERIMENT_TEMP_ROOT__, __JIKES_EXPERIMENT_ORIGINAL_ROOT__, __JIKES_EXPERIMENT_TEMP_BIN__

    # Create temporary folder
    __JIKES_EXPERIMENT_TEMP_ROOT__ = tempfile.mkdtemp(prefix='jikes-experiment-')

    # git clone
    args = ['git', 'clone', __JIKES_EXPERIMENT_ORIGINAL_ROOT__, __JIKES_EXPERIMENT_TEMP_ROOT__]
    subprocess.call(args)

    # change to temp root
    os.chdir(__JIKES_EXPERIMENT_TEMP_ROOT__)

    # git checkout
    args = ['git', 'checkout', commit]
    subprocess.call(args)

    # replace the host configuration files in the new repository with a symlink to the main one,
    # so that local changes to paths etc. can be propagated without having to include them in other branches.
    shutil.rmtree(os.path.join(__JIKES_EXPERIMENT_TEMP_ROOT__, 'build', 'hosts'))

    args = ['ln', '-s',
            os.path.join(__JIKES_EXPERIMENT_ORIGINAL_ROOT__, 'build', 'hosts'),
            os.path.join(__JIKES_EXPERIMENT_TEMP_ROOT__, 'build', 'hosts')]
    subprocess.call(args)

    # create a symlink to this original repo's components folder to avoid duplicate downloads
    args = ['ln', '-s',
            os.path.join(__JIKES_EXPERIMENT_ORIGINAL_ROOT__, 'components'),
            os.path.join(__JIKES_EXPERIMENT_TEMP_ROOT__, 'components')]
    subprocess.call(args)

    # build jikes
    __JIKES_EXPERIMENT_TEMP_BIN__ = os.path.join(__JIKES_EXPERIMENT_TEMP_ROOT__, build_jikes('x86_64-linux', 'development'))

    print "setting __JIKES_EXPERIMENT_TEMP_ROOT__ to", __JIKES_EXPERIMENT_TEMP_ROOT__

    return __JIKES_EXPERIMENT_TEMP_ROOT__

def build_jikes(host = 'x86_64-linux', config = 'development'):
    args = ['ant',
            '-Dhost.name=' + host,
            '-Dconfig.name=' + config,
            '-Djunit.jar=components/junit/4.10/junit4.10/junit-4.10.jar']
    subprocess.call(args)

    return os.path.join('dist', config + '_' + host)

def teardown():
    ''' deletes the temporary folder if one was created in checkout_and_build_jikes and changes
    back to the original repository root '''
    global __JIKES_EXPERIMENT_TEMP_ROOT__, __JIKES_EXPERIMENT_ORIGINAL_ROOT__

    os.chdir(__JIKES_EXPERIMENT_ORIGINAL_ROOT__)

    # If no-delete is set, simply print the name of the temp dir for later use, and return early
    if __ARGS__.no_delete:
        print __JIKES_EXPERIMENT_TEMP_ROOT__
        return

    # Otherwise, delete the temp root if one has been set.
    if (__JIKES_EXPERIMENT_TEMP_ROOT__):
        print "Deleting temporary folder", __JIKES_EXPERIMENT_TEMP_ROOT__
        shutil.rmtree(__JIKES_EXPERIMENT_TEMP_ROOT__)
        __JIKES_EXPERIMENT_TEMP_ROOT__ = ''
    else:
        print 'WARN', 'teardown() called with no temporary repository root previously defined.'

def run_rvm(args):
    ''' runs the rvm with the given arguments and returns the output as a string. '''
    # generate timelimit arguments if needed
    timelimit_args = []
    if __TIMELIMIT__ > 0:
        timelimit_args = ['timelimit', '-t', str(__TIMELIMIT__), '-T', str(1)]

    # generate rvm arguments using absolute paths if needed
    rvm_exec = 'rvm'
    bootclasspath = '-Xbootclasspath/p:' + os.path.join(__JIKES_EXPERIMENT_TEMP_ROOT__, __MONGO_DRIVER_JAR__)

    if (__JIKES_EXPERIMENT_TEMP_BIN__):
        rvm_exec = os.path.join(__JIKES_EXPERIMENT_TEMP_BIN__, rvm_exec)
    else:
        print 'WARN', '__JIKES_EXPERIMENT_TEMP_BIN__ is not set'

    rvm_args = [rvm_exec, bootclasspath]

    # build up all arguments
    args = timelimit_args + rvm_args + args

    # print executed command:
    print string.join(args)

    try:
        return subprocess.check_output(args, stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError as error:
        print 'ERROR', 'child process exited with non-zero exit code', error.returncode
        return error.output

def run_dacapo(benchmark, size='default', repetitions=1, vm_args=[], dacapo_args=[]):
    ''' runs the specified dacapo instance on the newly built rvm and returns
    its runtime in milliseconds. Use vm_args to specify -use_aosdb etc. '''
    dacapo_path = download_dacapo()

    # define the benchmark arguments, prepend any extra arguments to the vm
    args = vm_args + ['-jar', dacapo_path, benchmark, '-s', size, '-n', str(repetitions)] + dacapo_args

    # run the RVM with these arguments
    output = run_rvm(args)

    # attempt to find the PASSED time reported by the dacapo benchmark.
    # report an error and log the runtime as -1 if this line is missing from the output.
    time = 0
    try:
        time = int(re.findall(r'PASSED in (\d+)', output)[0])
    except IndexError:
        time = -1
        print 'ERROR', 'dacapo benchmark may have failed, could not determine PASSED time, recording -1'

    return time

def download_dacapo():
    ''' Downloads the dacapo benchmark to the original repository root returns the path to it. '''
    dacapo_path = os.path.abspath(os.path.join(__JIKES_EXPERIMENT_ORIGINAL_ROOT__, 'dacapo.jar'))

    if not os.path.exists(dacapo_path):
        urllib.urlretrieve(__DACAPO_DOWNLOAD__, dacapo_path)

    return dacapo_path

def drop_mongo_collection(database, collection):
    ''' uses the mongo shell to drop the specified collection from a database. '''
    args = ['mongo']
    commands = ['use '+database, 'db.'+collection+'.drop()', 'exit']

    p = subprocess.Popen(args, stdin=subprocess.PIPE)
    p.communicate(string.join(commands, '\n'))
    p.wait()


# These methods, open_csv, write_csv, close_csv must be called in this order to ensure only one file is open a time.
def open_csv(basename):
    global __CSV_FILE__, __CSV_WRITER__
    __CSV_FILE__ = open(os.path.join(__RESULTS_DIR__, basename+'.csv'), 'wb')
    __CSV_WRITER__ = csv.writer(__CSV_FILE__, delimiter=',')

def write_csv(values):
    ''' writes a list of values to the csv file and to standard output. '''
    if __CSV_WRITER__:
        __CSV_WRITER__.writerow(values)
    else:
        print 'ERROR', 'write_csv called with no csv file opened. Call open_csv(basename) first.'
    print string.join(map(str, values), ',')

def close_csv():
    global __CSV_WRITER__
    __CSV_WRITER__ = None
    __CSV_FILE__.close()
