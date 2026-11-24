#!/usr/bin/env bash
set -euo pipefail

: "${STUDENT_ID:?Set STUDENT_ID (used as the resource prefix)}"
: "${ROCKETMQ_HOME:?Set ROCKETMQ_HOME to a RocketMQ 5.5+ distribution}"

NAMESRV="${NAMESRV:-127.0.0.1:9876}"
MQADMIN="$ROCKETMQ_HOME/bin/mqadmin"
TASK_TOPIC="rmqai-${STUDENT_ID}-review-task"
RESULT_TOPIC="rmqai-${STUDENT_ID}-review-result"
SESSION="${1:-}"

echo "== cluster"
sh "$MQADMIN" clusterList -n "$NAMESRV" | head -5 || true
echo
echo "== task topic"
sh "$MQADMIN" getTopicConfig -n "$NAMESRV" -t "$TASK_TOPIC" | head -12 || true
echo
echo "== Lite parent topic"
sh "$MQADMIN" getTopicConfig -n "$NAMESRV" -t "$RESULT_TOPIC" | head -12 || true
echo
echo "== Lite group binding"
sh "$MQADMIN" getLiteGroupInfo -n "$NAMESRV" -p "$RESULT_TOPIC" \
  -g "GID_${STUDENT_ID}_supervisor" | head -8 || true

if [[ -n "$SESSION" ]]; then
  echo
  echo "== result message keys (session=$SESSION)"
  for dimension in STYLE LOGIC SECURITY SUMMARY; do
    key="${STUDENT_ID}_${SESSION}_${dimension}_RES"
    echo "-- $key"
    sh "$MQADMIN" queryMsgByKey -n "$NAMESRV" -t "$RESULT_TOPIC" -k "$key" | head -4 || true
  done
fi