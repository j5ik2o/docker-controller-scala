package com.github.j5ik2o.dockerController

import org.scalatest._

trait DockerControllerSpecSupport extends SuiteMixin with DockerControllerHelper with RandomPortUtil {
  this: TestSuite =>

  protected def createRemoveLifecycle: DockerContainerCreateRemoveLifecycle.Value =
    DockerContainerCreateRemoveLifecycle.ForEachTest

  protected def startStopLifecycle: DockerContainerStartStopLifecycle.Value =
    DockerContainerStartStopLifecycle.ForEachTest

  protected def createDockerContainers(
      createRemoveLifecycle: DockerContainerCreateRemoveLifecycle.Value,
      testName: Option[String]
  ): Boolean = {
    if (this.createRemoveLifecycle == createRemoveLifecycle) {
      beforeCreateContainers()
      for (dockerController <- dockerControllers) {
        createDockerContainer(dockerController, testName)
      }
      afterCreateContainers()
      true
    } else false
  }

  protected def startDockerContainers(
      startStopLifecycle: DockerContainerStartStopLifecycle.Value,
      testName: Option[String]
  ): Boolean = {
    if (this.startStopLifecycle == startStopLifecycle) {
      beforeStartContainers()
      for (dockerController <- dockerControllers) {
        startDockerContainer(dockerController, testName)
      }
      afterStartContainers()
      true
    } else false
  }

  protected def stopDockerContainers(
      startStopLifecycle: DockerContainerStartStopLifecycle.Value,
      testName: Option[String]
  ): Boolean = {
    if (this.startStopLifecycle == startStopLifecycle) {
      beforeStopContainers()
      for (dockerController <- dockerControllers) {
        stopDockerContainer(dockerController, testName)
      }
      afterStopContainers()
      true
    } else false
  }

  protected def removeDockerContainers(
      createRemoveLifecycle: DockerContainerCreateRemoveLifecycle.Value,
      testName: Option[String]
  ): Boolean = {
    if (this.createRemoveLifecycle == createRemoveLifecycle) {
      beforeRemoveContainers()
      for (dockerController <- dockerControllers) {
        removeDockerContainer(dockerController, testName)
      }
      afterRemoveContainers()
      true
    } else false
  }

  protected def beforeCreateContainers(): Unit = {}
  protected def afterCreateContainers(): Unit  = {}
  protected def beforeStartContainers(): Unit  = {}
  protected def afterStartContainers(): Unit   = {}
  protected def beforeStopContainers(): Unit   = {}
  protected def afterStopContainers(): Unit    = {}
  protected def beforeRemoveContainers(): Unit = {}
  protected def afterRemoveContainers(): Unit  = {}

  protected def disposeResources(): Unit = {
    dockerControllers.foreach(_.dispose())
    logger.debug("dockerClient#close")
    dockerClient.close()
  }

  abstract override def run(testName: Option[String], args: Args): Status = {
    (createRemoveLifecycle, startStopLifecycle) match {
      case (DockerContainerCreateRemoveLifecycle.ForEachTest, DockerContainerStartStopLifecycle.ForAllTest) =>
        throw new Error(s"Incorrect lifecycle settings: ($createRemoveLifecycle, $startStopLifecycle)")
      case _ =>
    }
    if (expectedTestCount(args.filter) == 0) {
      new CompositeStatus(Set.empty)
    } else {
      var created = false
      var started = false
      try {
        created = createDockerContainers(DockerContainerCreateRemoveLifecycle.ForAllTest, testName)
        started = startDockerContainers(DockerContainerStartStopLifecycle.ForAllTest, testName)
        super.run(testName, args)
      } finally {
        try {
          if (started)
            stopDockerContainers(DockerContainerStartStopLifecycle.ForAllTest, testName)
        } finally {
          try {
            if (created)
              removeDockerContainers(DockerContainerCreateRemoveLifecycle.ForAllTest, testName)
          } finally {
            afterRemoveContainers()
            disposeResources()
          }
        }
      }
    }
  }

  abstract protected override def runTest(testName: String, args: Args): Status = {
    var created = false
    var started = false
    try {
      created = createDockerContainers(DockerContainerCreateRemoveLifecycle.ForEachTest, Some(testName))
      started = startDockerContainers(DockerContainerStartStopLifecycle.ForEachTest, Some(testName))
      super.runTest(testName, args)
    } finally {
      try {
        if (started)
          stopDockerContainers(DockerContainerStartStopLifecycle.ForEachTest, Some(testName))
      } finally {
        if (created)
          removeDockerContainers(DockerContainerCreateRemoveLifecycle.ForEachTest, Some(testName))
      }
    }
  }
}
