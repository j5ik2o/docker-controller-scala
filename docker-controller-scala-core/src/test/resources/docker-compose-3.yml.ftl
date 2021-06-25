version: '3'
services:
  nginx:
    image: bithavoc/hello-world-env
    ports:
      - ${hostPort}:3000
    env_file:
      - ./settings-${id}.env