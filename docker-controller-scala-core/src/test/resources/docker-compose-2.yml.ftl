version: '3'
services:
  nginx:
    image: nginx
    ports:
      - ${nginxHostPort}:80