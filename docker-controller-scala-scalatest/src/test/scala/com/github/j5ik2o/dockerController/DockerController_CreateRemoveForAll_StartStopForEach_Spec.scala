package com.github.j5ik2o.dockerController

class DockerController_CreateRemoveForAll_StartStopForEach_Spec extends DockerControllerSpecBase {

  override def createRemoveLifecycle: DockerContainerCreateRemoveLifecycle.Value =
    DockerContainerCreateRemoveLifecycle.ForAllTest

  override def startStopLifecycle: DockerContainerStartStopLifecycle.Value =
    DockerContainerStartStopLifecycle.ForEachTest

}
