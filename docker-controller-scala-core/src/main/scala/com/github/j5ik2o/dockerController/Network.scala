package com.github.j5ik2o.dockerController

case class Network(id: String)
case class NetworkAlias(network: Network, name: String)
