Jenkins Build Flow Plugin
=========================

Fork of https://github.com/jenkinsci/build-flow-plugin with additional DSL for defining a flow in form of a directional
graph where vertices defines jobs to run and edges represents dependencies between jobs. It only works for non cyclic 
graphs.

### Gumtree Release

Push your changes and build a new version it using http://jenkins.qa.gt.ecg.so/view/All/job/jenkins-build-flow-plugin/.

### Usage

#### Start a build from the root job
Assume a graph ```job0 -> job1 -> job2``` and we want to start from the root ```job0```
```
build(graph(["job0", "job1"], ["job1", "job2"]), ["job0"])
```

#### Start a build from a non root job
2nd argument to the build method represents a job(s) to start build from (all jobs on the left side of those jobs
will not be executed)
```
build(graph(["job0", "job1"], ["job1", "job2"]), ["job1"])
```

#### Start a build from a non root job but force build on parent jobs(upstream jobs)
3nd argument to the build method represents a job(s) that we would like to build if any of the start job depends on them. 
Jobs that are not required by start jobs will be ignored. 
```
def mustJobs = ["j2"]
def shouldJobs = ["j0"]
def dGraph = graph(["jRoot", "j0"], ["j0", "j1"], ["j0", "jOut"], ["j1", "j2"], ["j2", "j3"])
build(dGraph, mustJobs, shouldJobs)
```

will build j0,j1,j2,j3 - for each of them there is a path from/to j2 
will not build jOut - there no path from/to j2
will not build jRoot 
    - is not a node on any path from any of the should build job to any of the must build job
    - it is parent of j2 but its build is not force because it's not in the shouldBuild jobs list 

#### Read a start job from a build param
Assume there is build param ```START_JOB= "job2"```
```
build(graph(["job0", "job1"], ["job1", "job2"]), [params["START_JOB"]])
```

#### Read start jobs from a build list parameter
Assume there is a build param ```START_JOBS="job1,job2"```
```
def mustJobs = params["START_JOBS"].split(",").toList()
build(graph(["job0", "job1"], ["job1", "job2"]), mustJobs)
```

#### Load a graph definition from java property file

Assume there is a property file at ```http://jenkins.example.com/example-graph.properties``` location with
the following content where key is a source(upstream) job and value is comma separated list of target(downstream) jobs:

```
job0=job1,job2,job5
job2=job3
job5=job6,job7
```

then it can be used to create a graph like this:

```
build(graph("http://jenkins.example.com/example-graph.properties"), ["job5"])
```

ORIGINAL README
===============
This Jenkins plugin allows managing jobs orchestration using a dedicated DSL, extracting the flow logic from jobs.

[![Build Status](https://jenkins.ci.cloudbees.com/job/plugins/job/build-flow-plugin/badge/icon)](https://jenkins.ci.cloudbees.com/job/plugins/job/build-flow-plugin/)

## Sample Build Flow Content ##

    parallel (
      {
        guard {
            build("job1A")
        } rescue {
            build("job1B")
        }
      },
      {
        retry 3, {
            build("job2")
        }
      }
    )

See the documentation and release notes at [Build Flow Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Build+Flow+Plugin) on the Jenkins Wiki for more information.

Other informations:
* Bug Tracker for known issues and expectations : [Jenkins Build Flow Component](https://issues.jenkins-ci.org/browse/JENKINS/component/16533)
* Discussions on this plugin are hosted on  [jenkins-user mailing list](https://wiki.jenkins-ci.org/display/JENKINS/Mailing+Lists)


Configuration
=============

After installing the plugin, you'll get a new Entry in the job creation wizard to create a Flow. Use the DSL editor to define the flow.

Basics
=======

The DSL defines the sequence of jobs to be built :

    build( "job1" )
    build( "job2" )
    build( "job3" )

You can pass parameters to jobs, and get the resulting `AbstractBuild` when required :

    b = build( "job1", param1: "foo", param2: "bar" )
    build( "job2", param1: b.build.number )
    build(param1: "xxx", param2: "yyy", param3: "zzz", "job3")
    build(param1: "xxx", "job4", param2: "yyy", param3: "zzz")
    def myBuildParams = [param1:"xxx", param2:"yyy", param3:"zzz"]
    build(myBuildParams, "job5")


Environment variables from a job can be obtained using the following, which is especially useful for getting things like the checkout revision used by the SCM plugin (`P4_CHANGELIST`, `GIT_REVISION`, etc) :

    def revision = b.environment.get( "GIT_REVISION" )

You can also access some pre-defined variables in the DSL :
* `build` the current flow execution
* `out` the flow build console
* `env` the flow environment, as a Map
* `params` triggered parameters
* `upstream` the upstream job, assuming the flow has been triggered as a downstream job for another job.

For example:

    // output values
    out.println 'Triggered Parameters Map:'
    out.println params
    out.println 'Build Object Properties:'
    build.properties.each { out.println "$it.key -> $it.value" }
    
    // output git commit info (git plugin)
    out.println build.environment.get('GIT_COMMIT')
    
    // use it in the flow
    build("job1", parent_param1: params["param1"])
    build("job2", parent_workspace:build.workspace)
    build(params, "job3")


## Guard / Rescue ##
You may need to run a cleanup job after a job (or set of jobs) whenever they succeeded or not. The `guard`/`rescue` structure is designed for this use-case. It works mostly like a try+finally block in Java language :

    guard {
        build( "this_job_may_fail" )
    } rescue {
        build( "cleanup" )
    }

The flow result will then be the worst of the guarded job(s) result and the rescue ones

## Ignore ##
You may also want to just ignore result of some job, that are optional for your build flow. You can use `ignore` block for this purpose :

    ignore(FAILURE) {
        build( "send_twitter_notification" )
    }

The flow will not take care of the triggered build status if it's better than the configured result. This allows you to ignore `UNSTABLE` < `FAILURE` < `ABORTED`

## Retry ##
You can ask the flow to `retry` a job a few times until success. This is equivalent to the retry-failed-job plugin :

    retry ( 3 ) {
        build( "this_job_may_fail" )
    }

## Parallel ##
The flow is strictly sequential, but let you run a set of jobs in parallel and wait for completion when using a `parallel` call. This is equivalent to the join plugin :

    parallel (
        // job 1, 2 and 3 will be scheduled in parallel.
        { build("job1") },
        { build("job2") },
        { build("job3") }
    )
    
    // job4 will be triggered after jobs 1, 2 and 3 complete
    build("job4")

compared to join plugin, parallel can be used for more complex workflows where the parallel branches can sequentially chain multiple jobs :

    parallel (
        {
            build("job1A")
            build("job1B")
            build("job1C")
        },
        {
            build("job2A")
            build("job2B")
            build("job2C")
        }
    )

you also can "name" parallel executions, so you can later use reference to extract parameters / status :

    join = parallel ([
            first:  { build("job1") },
            second: { build("job2") },
            third:  { build("job3") }
    ])
    
    // now, use results from parallel execution
    build("job4",
           param1: join.first.result.name,
           param2: join.second.lastBuild.parent.name)

and this can be combined with other orchestration keywords :

    parallel (
        {
            guard {
                build("job1A")
            } rescue {
                build("job1B")
            }
        },
        {
            retry 3, {
                build("job2")
            }
        }
    )

Extension Point
===============

Other plugins that expose themselves to the build flow can be accessed with extension.'plugin-name'

So the plugin foobar might be accessed like:

    def x = extension.'my-plugin-name'
    x.aMethodOnFoobarObject()

## Implementing Extension ##

Write the extension in your plugin

    @Extension(optional = true)
    public class MyBuildFlowDslExtension extends BuildFlowDSLExtension {
    
        /**
         * The extensionName to use for the extension.
         */
        public static final String EXTENSION_NAME = "my-plugin-name";
    
        @Override
        public Object createExtension(String extensionName, FlowDelegate dsl) {
            if (EXTENSION_NAME.equals(extensionName)) {
                return new MyBuildFlowDsl(dsl);
            }
            return null;
        }
    }

Write the actual extension

    public class MyBuildFlowDsl {
        private FlowDelegate dsl;
    
        /**
         * Standard constructor.
         * @param dsl the delegate.
         */
        public MyBuildFlowDsl(FlowDelegate dsl) {
            this.dsl = dsl;
        }
    
        /**
         * World.
         */
        public void hello() {
            ((PrintStream)dsl.getOut()).println("Hello World");
        }
    
    }

## Plugins implementing extension points ##

searching github for `BuildFlowDSLExtension`:
* https://github.com/jniesen/build-flow-json-parser-extension-plugin
* https://github.com/dnozay/build-flow-toolbox-plugin
* https://github.com/jenkinsci/external-resource-dispatcher-plugin
* https://github.com/jniesen/build-flow-http-extension-plugin
* https://github.com/jenkinsci/buildflow-extensions-plugin

