#!/bin/sh
set -eu

gateway_ipv4="$(awk '$2 == "host.docker.internal.v4" && $1 ~ /^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$/ { print $1; exit }' /etc/hosts)"
if [ -z "$gateway_ipv4" ]; then
    echo "No IPv4 host-gateway entry found for host.docker.internal.v4" >&2
    exit 1
fi

sed "s/__HOST_GATEWAY_IPV4__/$gateway_ipv4/g" \
    /etc/nginx/dev.conf > /etc/nginx/conf.d/default.conf
echo "Configured Nginx host gateway IPv4: $gateway_ipv4"
