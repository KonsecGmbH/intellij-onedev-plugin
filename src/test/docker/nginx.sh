docker run --name nginx-mtls \
  -d -p 8443:443 \
  -v /host/path/nginx.conf:/etc/nginx/nginx.conf:ro -d nginx
