docker run --name onedev -d --restart always \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e initial_user='test' \
  -e initial_password='test' \
  -e initial_email='test@example.com' \
  -e initial_server_url='http://127.0.0.1:6610/' \
  -v $(pwd)/onedev:/opt/onedev -p 6610:6610 -p 6611:6611 1dev/server
