#!/usr/bin/env bash
# lib.sh — A파트 테스트 공통 함수 (C 작성)
# 사용: 다른 스크립트에서  source ./lib.sh
#
# - 사용자마다 쿠키 파일을 따로 쓴다: cookies/H.txt, cookies/M.txt ...
# - 요청 본문은 파일로 보내서 한글이 깨지지 않게 한다 (Windows Git Bash 대비)
# - t 함수가 기대 상태·오류 코드와 비교해 PASS / FAIL 을 찍는다

BASE="${BASE:-http://localhost:8081}"
PW="test1234!"
mkdir -p cookies tmp

# 테스트 계정 (loginId는 영문·숫자만 가능해서 'testa' 접두사, 닉네임은 TEST_ 접두사)
declare -A LOGIN=( [H]=testah [M]=testam [M2]=testam2 [N]=testan [L]=testal [X1]=testax1 [X2]=testax2 [X3]=testax3 )
declare -A NICK=(  [H]=TEST_방장 [M]=TEST_참여 [M2]=TEST_참여2 [N]=TEST_외부 [L]=TEST_나감 [X1]=TEST_동시1 [X2]=TEST_동시2 [X3]=TEST_동시3 )

PASS=0; FAIL=0
LAST=""; LAST_STATUS=""

# call USER METHOD PATH [JSON]  → LAST(본문), LAST_STATUS(HTTP 상태)
call() {
  local user="$1" method="$2" path="$3" json="$4" args=()
  args=(-s -X "$method" -c "cookies/$user.txt" -b "cookies/$user.txt" -o tmp/body.json -w '%{http_code}')
  if [ -n "$json" ]; then
    printf '%s' "$json" > tmp/req.json
    args+=(-H 'Content-Type: application/json; charset=UTF-8' --data-binary @tmp/req.json)
  fi
  LAST_STATUS=$(curl "${args[@]}" "$BASE$path")
  LAST=$(cat tmp/body.json 2>/dev/null)
}

# JSON에서 숫자 필드 하나 꺼내기 (첫 번째 값):  num partyId
num() { printf '%s' "$LAST" | sed -n "s/.*\"$1\":\([0-9-]*\).*/\1/p" | head -1; }
# 오류 코드 꺼내기
err() { printf '%s' "$LAST" | sed -n 's/.*"error":"\([A-Z_]*\)".*/\1/p'; }

# t "라벨" USER METHOD PATH JSON 기대상태 기대오류(-면 없음) [응답에 꼭 있어야 할 문자열...]
t() {
  local label="$1" user="$2" method="$3" path="$4" json="$5" exp_status="$6" exp_err="$7"
  shift 7
  call "$user" "$method" "$path" "$json"
  local e; e=$(err)
  local ok=1 why=""
  [ "$LAST_STATUS" = "$exp_status" ] || { ok=0; why="상태 $LAST_STATUS(기대 $exp_status)"; }
  if [ "$exp_err" != "-" ] && [ "$e" != "$exp_err" ]; then ok=0; why="$why 오류 ${e:-없음}(기대 $exp_err)"; fi
  for must in "$@"; do
    printf '%s' "$LAST" | grep -qF -- "$must" || { ok=0; why="$why [$must] 없음"; }
  done
  if [ $ok = 1 ]; then
    PASS=$((PASS+1)); printf '  PASS  %s  (%s %s)\n' "$label" "$LAST_STATUS" "${e:-OK}"
  else
    FAIL=$((FAIL+1)); printf '  FAIL  %s  → %s\n        응답: %.400s\n' "$label" "$why" "$LAST"
  fi
}

section() { printf '\n===== %s =====\n' "$1"; }
info() { printf '  ·  %s\n' "$1"; }

# 식사 시간 고르기: 지금 기준으로 시간대 안의 30분 단위 슬롯
#   MEAL     : 쓸 끼니 (점심 슬롯이 남았으면 LUNCH, 아니면 DINNER)
#   T1, T2   : 지금+60분 이후 첫 슬롯과 그다음 슬롯 (확정 취소 마감 여유)
#   WRONG    : 다른 끼니 시간 (INVALID_MEAL_TIME 확인용)
#   PAST     : 같은 끼니 중 이미 지난 슬롯 (없으면 빈 값 → 보류)
pick_times() {
  local now; now=$((10#$(date +%H) * 60 + 10#$(date +%M)))
  local lunch=(660 690 720 750 780 810) dinner=(1020 1050 1080 1110 1140 1170)
  local slots s
  if [ $((now + 90)) -le 810 ]; then MEAL=LUNCH; slots=("${lunch[@]}"); WRONG="18:00"
  elif [ $((now + 90)) -le 1170 ]; then MEAL=DINNER; slots=("${dinner[@]}"); WRONG="12:00"
  else echo "지금은 테스트 가능한 식사 시간이 없습니다 (19:30 이후). 내일 다시 실행해 주세요."; exit 1; fi
  T1=""; T2=""; PAST=""
  for s in "${slots[@]}"; do
    if [ $s -lt $now ]; then PAST=$(printf '%02d:%02d' $((s/60)) $((s%60))); fi
    if [ $s -ge $((now + 60)) ]; then
      if [ -z "$T1" ]; then T1=$(printf '%02d:%02d' $((s/60)) $((s%60)))
      elif [ -z "$T2" ]; then T2=$(printf '%02d:%02d' $((s/60)) $((s%60))); fi
    fi
  done
  [ -z "$T2" ] && T2="$T1"
}

summary() { printf '\n===== 결과: PASS %d / FAIL %d =====\n' "$PASS" "$FAIL"; }
