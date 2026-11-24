#!/usr/bin/env bash
set -euo pipefail

: "${STUDENT_ID:?Set STUDENT_ID (used as the resource prefix)}"
: "${ROCKETMQ_HOME:?Set ROCKETMQ_HOME to a RocketMQ 5.5+ distribution}"

NAMESRV="${NAMESRV:-127.0.0.1:9876}"
CLUSTER="${CLUSTER:-DefaultCluster}"
MQADMIN="$ROCKETMQ_HOME/bin/mqadmin"
TASK_TOPIC="rmqai-${STUDENT_ID}-review-task"
RESULT_TOPIC="rmqai-${STUDENT_ID}-review-result"

if [[ ! -x "$MQADMIN" ]]; then
  echo "mqadmin not found: $MQADMIN" >&2
  exit 1
fi

echo "== create normal task topic: $TASK_TOPIC"
sh "$MQADMIN" updateTopic -n "$NAMESRV" -c "$CLUSTER" -t "$TASK_TOPIC" \
  -a +message.type=NORMAL >/dev/null

echo "== create LITE parent topic: $RESULT_TOPIC"
sh "$MQADMIN" updateTopic -n "$NAMESRV" -c "$CLUSTER" -t "$RESULT_TOPIC" \
  -a +message.type=LITE >/dev/null

for dimension in style logic security summary; do
  group="GID_${STUDENT_ID}_${dimension}"
  echo "== create normal consumer group: $group"
  sh "$MQADMIN" updateSubGroup -n "$NAMESRV" -c "$CLUSTER" -g "$group" >/dev/null
done

echo "== create lite consumer group: GID_${STUDENT_ID}_supervisor"
sh "$MQADMIN" updateSubGroup -n "$NAMESRV" -c "$CLUSTER" \
  -g "GID_${STUDENT_ID}_supervisor" \
  --attributes "+lite.bind.topic=${RESULT_TOPIC}" >/dev/null

echo
echo "Resources ready for prefix: $STUDENT_ID"
echo "  task   : $TASK_TOPIC"
echo "  result : $RESULT_TOPIC (Lite)"
echo "  groups : GID_${STUDENT_ID}_{style,logic,security,summary,supervisor}"