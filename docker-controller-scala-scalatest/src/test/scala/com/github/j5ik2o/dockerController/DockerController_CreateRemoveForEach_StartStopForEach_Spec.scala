package com.github.j5ik2o.dockerController

class DockerController_CreateRemoveForEach_StartStopForEach_Spec extends DockerControllerSpecBase {

  override def createRemoveLifecycle: DockerContainerCreateRemoveLifecycle.Value =
    DockerContainerCreateRemoveLifecycle.ForEachTest

  override def startStopLifecycle: DockerContainerStartStopLifecycle.Value =
    DockerContainerStartStopLifecycle.ForEachTest

}
