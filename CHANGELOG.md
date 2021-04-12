## [1.0.4](https://github.com/j5ik2o/docker-controller-scala/compare/v1.0.3...v1.0.4) (2021-04-12)

### Features

* Add support for Minio module ([bc79771](https://github.com/j5ik2o/docker-controller-scala/commit/bc797713cbd07a5fc2197567bb489d1f27e46c7a))
* add waitPredicatesSettings to DynamoDBLocalControllerSpec, Therefore, overriding startDockerCo ([4a5c7f3](https://github.com/j5ik2o/docker-controller-scala/commit/4a5c7f3353f46b7d649f06da7eb39e2bb7245e6e))

## [1.0.3](https://github.com/j5ik2o/docker-controller-scala/compare/v1.0.2...v1.0.3) (2021-04-12)

### Bug Fixes

* Fix containerId method return type to Option[String] ([227534b](https://github.com/j5ik2o/docker-controller-scala/commit/227534b5579666de0333d5c870ae8f612daebbdd))
* fix version.sbt to slash style ([8f8078f](https://github.com/j5ik2o/docker-controller-scala/commit/8f8078f0e7ee904a561ad6618643e359e09fcae1))

### Features

* Add awaitDuration param to WaitPredicate methods ([a0a0c69](https://github.com/j5ik2o/docker-controller-scala/commit/a0a0c6977f8c473f941ea2cf9954944a943929d0))
* Add dockerHost method to DockerClientConfigUtil ([da3a3da](https://github.com/j5ik2o/docker-controller-scala/commit/da3a3da080187c5ae99db1550d2aedb91509eaf0))
* Add isSupportDockerMachine method to DockerClientConfigUtil ([3f7f58d](https://github.com/j5ik2o/docker-controller-scala/commit/3f7f58d50df30b00ff60ec33f83a3589a5b1e5e7))
* add support for dynamodb-local ([097f832](https://github.com/j5ik2o/docker-controller-scala/commit/097f8327e0c5660a667733d7852c8d49287b1f7e))
* Add support for me.tongfei.ProgressBar ([732be3a](https://github.com/j5ik2o/docker-controller-scala/commit/732be3a7951950219a67fe71524ac70865d2156c))
* Add WaitPredicates for as a strategy to wait for the container startup process ([c56c798](https://github.com/j5ik2o/docker-controller-scala/commit/c56c798fe1f72f969e4632556a43de50a836c8ed))

### BREAKING CHANGES

* Modify return type to Option[String]

## 1.0.2 (2021-04-09)

### Bug Fixes

* fix cross build error ([e36cc63](https://github.com/j5ik2o/docker-controller-scala/commit/e36cc63831113cef267a27b68e16d8849d869a2b))

### Features

* add configureCmds and configureCreateContainerCmd ([7da32c5](https://github.com/j5ik2o/docker-controller-scala/commit/7da32c5a32f106288a811abbc6d645e022e5917a))
* add support for docker-compose ([37fff3d](https://github.com/j5ik2o/docker-controller-scala/commit/37fff3d9ef176207a3ff1f6cf2001952e7c370bc))

## 1.0.1 (2021-04-09)

* remove Main object

## 1.0.0 (2021-04-08)

* first release
