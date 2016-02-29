# python 2.7
# beyond build requirements for Jikes, requires installation of the timelimit utility.

import os
import re
import csv
import subprocess
import tempfile
import string
import shutil
import urllib

# === configuration options ===
__DACAPO_DOWNLOAD__ = 'http://downloads.sourceforge.net/project/dacapobench/9.12-bach/dacapo-9.12-bach.jar?r=http%3A%2F%2Fsourceforge.net%2Fprojects%2Fdacapobench%2Ffiles%2F9.12-bach%2F&ts=1455100129&use_mirror=kent'

# === 'static' variables needed by module functions below - do not change ===
# set a temporary repository root if an experiment requires a specific commit checked out
__JIKES_EXPERIMENT_TEMP_ROOT__ = ''
__JIKES_EXPERIMENT_TEMP_BIN__ = ''

# keep the original repository root constant for returning here after teardown.
__JIKES_EXPERIMENT_ORIGINAL_ROOT__ = os.path.abspath(os.path.join(os.path.dirname(__file__),'..'))

def reset_root():
    ''' for manual inclusion of common.py, this function can be called to set the temp-root and temp-bin
    paths to be within the original root directory '''
    global __JIKES_EXPERIMENT_TEMP_ROOT__, __JIKES_EXPERIMENT_TEMP_BIN__
    __JIKES_EXPERIMENT_TEMP_ROOT__ = __JIKES_EXPERIMENT_ORIGINAL_ROOT__
    __JIKES_EXPERIMENT_TEMP_BIN__ = os.path.join(__JIKES_EXPERIMENT_ORIGINAL_ROOT__, 'dist/development_x86_64-linux/')

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

    if (__JIKES_EXPERIMENT_TEMP_ROOT__):
        print "Deleting temporary folder", __JIKES_EXPERIMENT_TEMP_ROOT__
        shutil.rmtree(__JIKES_EXPERIMENT_TEMP_ROOT__)
        __JIKES_EXPERIMENT_TEMP_ROOT__ = ''
    else:
        print 'WARN', 'teardown() called with no temporary repository root previously defined.'

def run_rvm(args, timelimit = 0):
    ''' runs the rvm with the given arguments and returns the output as a string. '''
    # generate timelimit arguments if needed
    timelimit_args = []
    if timelimit > 0:
        timelimit_args = ['timelimit', '-t', str(timelimit), '-T', str(1)]

    # generate rvm arguments using absolute paths if needed
    rvm_exec = 'rvm'
    bootclasspath = '-Xbootclasspath/p:' + os.path.join(__JIKES_EXPERIMENT_TEMP_ROOT__, 'mongo-java-driver-3.2.1.jar')

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

def run_dacapo(benchmark, size='default', repetitions=1, vm_args=[], dacapo_args=[], timelimit = 0):
    ''' runs the specified dacapo instance on the newly built rvm and returns
    its runtime in milliseconds. Use vm_args to specify -use_aosdb etc. '''
    dacapo_path = download_dacapo()

    # define the benchmark arguments, prepend any extra arguments to the vm
    args = vm_args + ['-jar', dacapo_path, benchmark, '-s', size, '-n', str(repetitions)] + dacapo_args

    # run the RVM with these arguments
    output = run_rvm(args, timelimit = timelimit)

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

def dropMongoCollection(database, collection):
    ''' uses the mongo shell to drop the specified collection from a database. '''
    args = ['mongo']
    commands = ['use '+database, 'db.'+collection+'.drop()', 'exit']

    p = subprocess.Popen(args, stdin=subprocess.PIPE)
    p.communicate(string.join(commands, '\n'))
    p.wait()
